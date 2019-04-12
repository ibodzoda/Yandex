package tj.abad.doru.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mail.MailException
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import tj.abad.doru.database.model.*
import tj.abad.doru.database.model.Drug
import tj.abad.doru.exception.UserExistsException
import tj.abad.doru.request.*
import tj.abad.doru.service.*
import javax.servlet.http.HttpServletResponse
import javax.validation.Valid

@RestController
@RequestMapping(value = ["drugstore"])
class DrugstoreController {
    @Autowired
    lateinit var userService: UserService
    @Autowired
    lateinit var drugstoreService: DrugstoreService
    @Autowired
    lateinit var drugService: DrugService
    @Autowired
    lateinit var drugDiscountService: DrugDiscountService
    @Autowired
    lateinit var drugInDrugstoreService: DrugInDrugstoreService

    @RequestMapping(value = ["registration"], method = [RequestMethod.POST], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun createDrugstore(@RequestPart("drugstore") @Valid drugstore: CreateDrugstoreRequest,
                        @RequestPart("image") @Valid image: MultipartFile, response: HttpServletResponse) {
        try {
            drugstoreService.createDrugstore(drugstore, image)
        } catch (e: UserExistsException) {
            response.status = HttpServletResponse.SC_CONFLICT
        } catch (e: MailException) {
            response.status = HttpServletResponse.SC_PRECONDITION_FAILED
        }
    }

    @RequestMapping(value = ["get-info"], method = [RequestMethod.GET])
    fun getDrugstoreInfo(response: HttpServletResponse): DrugstoreInfo? {
        val getDrugstoreInfo = drugstoreService.getDrugstoreInfo()
        if (getDrugstoreInfo == null)
            response.status = HttpServletResponse.SC_NOT_FOUND
        else
            return getDrugstoreInfo

        return null
    }

    @RequestMapping(value = ["edit"], method = [RequestMethod.POST], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun updateDrugstoreInfo(@RequestPart("updateDrugstoreInfo") @Valid updateDrugstoreInfo: UpdateDrugstoreInfo,
                            @RequestPart("image") @Valid image: MultipartFile?) {
        drugstoreService.updateDrugstoreInfo(updateDrugstoreInfo, image)
    }

    @RequestMapping(value = ["change-self-password"], method = [RequestMethod.POST])
    fun changeSelfPassword(@RequestBody passwordEditRequest: PasswordEditRequest, response: HttpServletResponse) {
        if (!drugstoreService.changeSelfPassword(passwordEditRequest)) {
            response.status = HttpServletResponse.SC_NOT_ACCEPTABLE
        }
    }

    @RequestMapping(value = ["{drugstoreId}"], method = [RequestMethod.GET])
    fun getDrugstoreInfo(@PathVariable drugstoreId: Int): DrugstoreProfileInfo? {
        return drugstoreService.getDrugstoreInfo(drugstoreId)
    }

    //@PreAuthorize("hasRole('DRUGSTORE')")
    @RequestMapping(value = ["drug/search"], method = [RequestMethod.POST])
    fun searchDrugs(@RequestBody drugSearch: DrugSearchRequest): List<Drug>? {
        val drugstoreId = SessionUtils.currentUser()?.id

        return if (drugstoreId != null)
            drugService.getDrugsContainsName(drugstoreId, drugSearch.drugName)
        else
            null
    }

    @RequestMapping(value = ["drug/add"], method = [RequestMethod.POST])
    fun addDrugToDrugstore(@RequestBody drug: DrugInDrugstoreRequest) {
        drugstoreService.addDrugToDrugstore(drug)
    }

    @RequestMapping(value = ["drug/update"], method = [RequestMethod.POST])
    fun updateDrugToDrugstore(@RequestBody drug: DrugInDrugstoreRequest) {
        drugstoreService.updateDrugToDrugstore(drug)
    }

    @RequestMapping(value = ["drug-discount/get/{drugId}"], method = [RequestMethod.GET])
    fun getDiscountByDrugId(@PathVariable drugId: Int): Discount? {
        val drugstoreId = SessionUtils.currentUser()?.id
        if (drugstoreId != null)
            return drugDiscountService.getAll(drugId, drugstoreId)
        return null
    }

    @RequestMapping(value = ["search"], method = [RequestMethod.GET])
    fun getDrugstoreInfoBySearch(@RequestParam name: String): List<Drugstore> {
        return drugstoreService.getDrugstoreInfoBySearch(name)
    }


    @RequestMapping(value = ["search-by-chosen-drugs"], method = [RequestMethod.POST])
    fun getDrugsFromOneDrugstore(@RequestBody drugId: DrugstoreDrugSearch, response: HttpServletResponse): List<DrugstoreDrug>? {
        val result = drugstoreService.getAllDrugsFromOneDrugstore(drugId.drugsId)
        if (result?.isEmpty()!!) {
            response.status = HttpServletResponse.SC_NOT_FOUND
        } else {
            return result
        }
        return null
    }

    @RequestMapping(value = ["search-by-chosen-drugs-and-drugstore"], method = [RequestMethod.POST])
    fun getDrugsByDrugstoreId(@RequestBody drugId: DrugSearchByDrugstoreId): List<DrugstoreDrug> {
        return drugstoreService.getDrugsByDrugstoreId(drugId.drugsId, drugId.drugstoreId)
    }

    @RequestMapping(value = ["drugs-search-from-several-drugstore"], method = [RequestMethod.POST])
    fun drugInDrugstore(@RequestBody drugs: DrugstoreDrugSearch, response: HttpServletResponse): List<DrugstoreDrug>? {
        val result = drugInDrugstoreService.drugInDrugstore(drugs.drugsId)
        if (result == null) {
            response.status = HttpServletResponse.SC_NOT_FOUND
        } else {
            return result
        }
        return null
    }
}