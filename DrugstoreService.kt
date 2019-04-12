package tj.abad.doru.service

import org.apache.commons.lang.RandomStringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import tj.abad.doru.database.model.DayInterval
import tj.abad.doru.database.model.Drugstore
import tj.abad.doru.database.model.DrugstoreDrug
import tj.abad.doru.database.model.DrugstoreInfo
import tj.abad.doru.database.repository.DrugstoreRepository
import tj.abad.doru.database.repository.DrugstoresWorkDayRepository
import tj.abad.doru.exception.PasswordNotMatch
import tj.abad.doru.exception.UserExistsException
import tj.abad.doru.request.*

@Service
class DrugstoreService {
    companion object {
        const val SMALL = "small"
        const val MEDIUM = "medium"
        val DAYS_MAP = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
    }

    @Value("\${doru.front.base-url}")
    lateinit var baseUrl: String
    @Value("\${doru.images.base-url}")
    lateinit var imagesBaseUrl: String
    @Autowired
    lateinit var drugstoreRepository: DrugstoreRepository
    @Autowired
    lateinit var fileUploadService: FileUploadService
    @Autowired
    lateinit var emailService: EmailService
    @Autowired
    lateinit var drugstoresWorkDayRepository: DrugstoresWorkDayRepository
    @Autowired
    lateinit var imageService: ImageService

    @Throws(UserExistsException::class, MailException::class)
    fun createDrugstore(drugstoreRequest: CreateDrugstoreRequest, image: MultipartFile?) {
        val confirmationCode = RandomStringUtils.random(20, true, true)
        val drugstoreId = drugstoreRepository.createDrugstore(drugstoreRequest, confirmationCode)
        emailService.sendMail(drugstoreRequest.email, "Drugstore Confirmation", "$baseUrl/registration/confirmation?email=${drugstoreRequest.email}&code=$confirmationCode")

        drugstoreRequest.workDays.forEach { workDay ->
            drugstoresWorkDayRepository.addWorkDaySchedule(drugstoreId, workDay)
        }

        if (image != null) {
            val fileName = generateFileName()
            this.fileUploadService.upload(image, fileName)
            drugstoreRepository.updateDrugstorePhotoPath(fileName, drugstoreId)

        }
    }

    fun generateFileName(): String {
        return RandomStringUtils.random(15, true, true)
    }

    fun getDrugstoreInfo(): DrugstoreInfo? {
        val drugstoreId = SessionUtils.currentUser()?.id
        return if (drugstoreId != null) {
            val drugstoreInfo = drugstoreRepository.getDrugstoreInfoByDrugstoreId(drugstoreId)
            drugstoreInfo?.drugstorePhotoUrl = getPhotoUrl(drugstoreInfo?.drugstorePhotoPath, SMALL)
            val drugstoreWorkDay = drugstoreRepository.getWorkDaysByDrugstoreId(drugstoreId)
            drugstoreWorkDay.forEach { workDay ->
                workDay.from = ("${workDay.from[0]}${workDay.from[1]}${workDay.from[2]}${workDay.from[3]}${workDay.from[4]}")
                workDay.until = ("${workDay.until[0]}${workDay.until[1]}${workDay.until[2]}${workDay.until[3]}${workDay.until[4]}")
            }

            DrugstoreInfo(drugstoreInfo!!, drugstoreWorkDay)
        } else {
            null
        }

    }

    fun getPhotoUrl(photoPath: String?, type: String) = if (photoPath != null) "$imagesBaseUrl/$photoPath/${photoPath}_$type" else null


    fun updateDrugstoreInfo(updateDrugstoreInfo: UpdateDrugstoreInfo, image: MultipartFile?) {
        val drugstoreId = SessionUtils.currentUser()?.id
        if (drugstoreId != null) {

            if (image != null) {
                imageService.deleteDrugstoreImage(drugstoreRepository.getFileNameById(drugstoreId))
                val fileName = generateFileName()
                fileUploadService.upload(image, fileName)
                drugstoreRepository.updateDrugstorePhotoPath(fileName, drugstoreId)
            }

            drugstoresWorkDayRepository.deleteAll(drugstoreId)
            updateDrugstoreInfo.workDaySchedule.forEach { workDay ->
                drugstoresWorkDayRepository.addWorkDaySchedule(drugstoreId, workDay)
            }

            drugstoreRepository.updateDrugstoreInfo(updateDrugstoreInfo, drugstoreId)
        }
    }

    @Throws(PasswordNotMatch::class)
    fun changeSelfPassword(passwordEditRequest: PasswordEditRequest): Boolean {
        val ownerId = SessionUtils.currentUser()?.id
        if (ownerId != null) {
            return drugstoreRepository.changeSelfPassword(passwordEditRequest, ownerId)
        }
        return false
    }


    fun getDrugstoreInfo(drugstoreId: Int): DrugstoreProfileInfo? {
        val currentUser = SessionUtils.currentUser()?.id
        val drugstore = drugstoreRepository.getDrugstoreInfo(drugstoreId)
        drugstore?.drugstoreOwner = currentUser == drugstoreId
        drugstore!!.drugstorePhotoUrl = getPhotoUrl(drugstore.drugstorePhotoPath, SMALL)
        val drugstoreWorkTime = drugstoreRepository.getDrugstoreWorkDayAndWorkTime(drugstoreId)
        val days = listOf<String>("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        val mp = mutableMapOf<String, Int>()
        val weekends = mutableListOf<String>()

        days.forEach { day ->
            drugstoreWorkTime.forEach { workDays ->
                workDays.workDay.forEach { work ->
                    if (work == day) {
                        mp[day] = 1
                    }
                }
            }
            if (mp[day] == null) {
                weekends.add(day)
                mp[day] = 1
            }
        }

        drugstoreWorkTime.forEach { workDay ->
            workDay.timeStart = ("${workDay.timeStart[0]}${workDay.timeStart[1]}${workDay.timeStart[2]}${workDay.timeStart[3]}${workDay.timeStart[4]}")
            workDay.timeEnd = ("${workDay.timeEnd[0]}${workDay.timeEnd[1]}${workDay.timeEnd[2]}${workDay.timeEnd[3]}${workDay.timeEnd[4]}")
        }

        drugstoreWorkTime.forEach { workDays ->

            val daysIndex = workDays.workDay.map {
                DAYS_MAP.indexOf(it)
            }

            val intervals = mutableListOf<tj.abad.doru.request.DayInterval>()
            var interval = tj.abad.doru.request.DayInterval(DAYS_MAP[daysIndex.first()])

            if (workDays.workDay.size > 1) {
                for (i in 0 until daysIndex.size - 1) {
                    if (daysIndex[i + 1] - daysIndex[i] != 1) {
                        interval.end = DAYS_MAP[daysIndex[i]]
                        intervals += interval
                        interval = tj.abad.doru.request.DayInterval(DAYS_MAP[daysIndex[i + 1]])
                    }
                }
            }

            if (interval.end == null) {
                interval.end = DAYS_MAP[daysIndex.last()]
                intervals += interval
            }
            workDays.daysInterval = intervals
        }

        return DrugstoreProfileInfo(drugstore, drugstoreWorkTime, weekends)
    }


    fun addDrugToDrugstore(drug: DrugInDrugstoreRequest) {
        val drugstoreId = SessionUtils.currentUser()?.id

        if (drugstoreId != null)
            drugstoreRepository.addDrugToDrugstore(drugstoreId, drug)
    }

    fun updateDrugToDrugstore(drug: DrugInDrugstoreRequest) {
        val drugstoreId = SessionUtils.currentUser()?.id

        if (drugstoreId != null)
            drugstoreRepository.updateDrugInfoInDrugstore(drugstoreId, drug)
    }

    fun getDrugstoreInfoBySearch(name: String): List<Drugstore> {
        val drugName = name.replace(',', ' ').split(' ')
                .map { "%$it%" }
                .toMutableList()

        val searchDrugstore = "LOWER(?) OR ".repeat(drugName.size).removeSuffix(" OR ")
        drugName.addAll(drugName)

        val drugstores = drugstoreRepository.searchDrugstore(drugName.toTypedArray(), searchDrugstore)
        drugstores.forEach { it.workDays = drugstoreRepository.getDrugstoreWorkDayAndTime(it.drugstoreId) }
        val daysOfWeek = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        val mp = mutableMapOf<String, Int>()
        drugstores.forEach { workDay ->
            val weekends = mutableListOf<String>()
            mp.clear()
            daysOfWeek.forEach { day ->
                workDay.workDays?.forEach { workdays ->
                    workdays.workDay.forEach { work ->
                        if (work == day) {
                            mp[day] = 1
                        }
                    }
                }
                if (mp[day] == null) {
                    weekends.add(day)
                    mp[day] = 1
                }
            }
            workDay.weekends = weekends
        }

        drugstores.forEach { drugstore ->
            drugstore.workDays?.forEach { workDay ->
                workDay.timeStart = ("${workDay.timeStart[0]}${workDay.timeStart[1]}${workDay.timeStart[2]}${workDay.timeStart[3]}${workDay.timeStart[4]}")
                workDay.timeEnd = ("${workDay.timeEnd[0]}${workDay.timeEnd[1]}${workDay.timeEnd[2]}${workDay.timeEnd[3]}${workDay.timeEnd[4]}")
            }
        }

        drugstores.forEach { drugstore ->
            drugstore.workDays?.forEach { days ->

                val daysIndex = days.workDay.map {
                    DAYS_MAP.indexOf(it)
                }

                val intervals = mutableListOf<DayInterval>()
                var interval = DayInterval(DAYS_MAP[daysIndex.first()])

                if (days.workDay.size > 1) {
                    for (i in 0 until daysIndex.size - 1) {
                        if (daysIndex[i + 1] - daysIndex[i] != 1) {
                            interval.end = DAYS_MAP[daysIndex[i]]
                            intervals += interval
                            interval = DayInterval(DAYS_MAP[daysIndex[i + 1]])
                        }
                    }
                }

                if (interval.end == null) {
                    interval.end = DAYS_MAP[daysIndex.last()]
                    intervals += interval
                }
                days.daysInterval = intervals
            }
        }
        return drugstores
    }

    fun getAllDrugsFromOneDrugstore(drugId: Array<Int>): List<DrugstoreDrug>? {
        val quantity = "?, ".repeat(drugId.size).removeSuffix(", ")
        val drugs = drugstoreRepository.getAllDrugsFromOneDrugstore(drugId, 0, quantity)

        drugs.forEach {
            it.drugsInfo = drugstoreRepository.getAllFromOne(drugId, it.drugstoreId, quantity)
            it.workDays = drugstoreRepository.getDrugstoreWorkDayAndTime(it.drugstoreId)
            workDaysAndWeekends(it)
        }
        return drugs
    }

    fun getDrugsByDrugstoreId(drugId: Array<Int>, drugstoreId: Int): List<DrugstoreDrug> {
        val quantity = "?, ".repeat(drugId.size).removeSuffix(", ")
        val drugs = drugstoreRepository.getAllDrugsFromOneDrugstore(drugId, drugstoreId, quantity)
        drugs.forEach {
            it.drugsInfo = drugstoreRepository.getAllFromOne(drugId, it.drugstoreId, quantity)
            it.workDays = drugstoreRepository.getDrugstoreWorkDayAndTime(it.drugstoreId)
            workDaysAndWeekends(it)
        }
        return drugs
    }

    fun workDaysAndWeekends(drugs: DrugstoreDrug): DrugstoreDrug {
        val daysOfWeek = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        val mp = mutableMapOf<String, Int>()
        val weekends = mutableListOf<String>()
        mp.clear()
        daysOfWeek.forEach { day ->
            drugs.workDays?.forEach { workdays ->
                workdays.workDay.forEach { work ->
                    if (work == day) {
                        mp[day] = 1
                    }
                }
            }
            if (mp[day] == null) {
                weekends.add(day)
                mp[day] = 1
            }
        }
        drugs.weekends = weekends

        drugs.workDays?.forEach { workDay ->
            workDay.timeStart = ("${workDay.timeStart[0]}${workDay.timeStart[1]}${workDay.timeStart[2]}${workDay.timeStart[3]}${workDay.timeStart[4]}")
            workDay.timeEnd = ("${workDay.timeEnd[0]}${workDay.timeEnd[1]}${workDay.timeEnd[2]}${workDay.timeEnd[3]}${workDay.timeEnd[4]}")
        }

        drugs.workDays?.forEach { days ->
            val daysIndex = days.workDay.map {
                DrugstoreService.DAYS_MAP.indexOf(it)
            }
            val intervals = mutableListOf<DayInterval>()
            var interval = DayInterval(DrugstoreService.DAYS_MAP[daysIndex.first()])

            if (days.workDay.size > 1) {
                for (i in 0 until daysIndex.size - 1) {
                    if (daysIndex[i + 1] - daysIndex[i] != 1) {
                        interval.end = DrugstoreService.DAYS_MAP[daysIndex[i]]
                        intervals += interval
                        interval = DayInterval(DrugstoreService.DAYS_MAP[daysIndex[i + 1]])
                    }
                }
            }

            if (interval.end == null) {
                interval.end = DrugstoreService.DAYS_MAP[daysIndex.last()]
                intervals += interval
            }
            days.daysInterval = intervals
        }
        return drugs
    }
}

