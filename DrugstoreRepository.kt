package tj.abad.doru.database.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.query
import org.springframework.jdbc.core.queryForObject
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Repository
import tj.abad.doru.database.model.*
import tj.abad.doru.exception.PasswordNotMatch
import tj.abad.doru.exception.UserExistsException
import tj.abad.doru.request.*
import java.sql.ResultSet

@Repository
class DrugstoreRepository {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate
    @Autowired
    lateinit var userRepository: UserRepository
    @Autowired
    lateinit var passwordEncoder: BCryptPasswordEncoder

    @Throws(UserExistsException::class)
    fun createDrugstore(drugstore: CreateDrugstoreRequest, confirmationCode: String): Int {
        val userId = userRepository.createUser(drugstore.getUser(), confirmationCode, userType = "DRUGSTORE")

        this.jdbcTemplate.update(
                """INSERT INTO drugstores(drugstore_id, drugstore_name, address, city_id, latitude, longitude)
                    VALUES(?, ?, ?, ?, ?, ?)""", userId, drugstore.drugstoreName, drugstore.address, drugstore.cityId, drugstore.latitude, drugstore.longitude)

        return userId
    }

    fun updateDrugstorePhotoPath(photoPath: String, drugstoreId: Int) {
        jdbcTemplate.update("UPDATE drugstores SET drugstore_photo_path=? WHERE drugstore_id=?", photoPath, drugstoreId)
    }

    fun getDrugstoreInfoByDrugstoreId(id: Int): DrugstoreProfile? {
        return try {
            jdbcTemplate.queryForObject("""SELECT u.name,
                             u.phone_number,
                             d.address,
                             d.drugstore_name,
                             d.drugstore_photo_path,
                             d.latitude,
                             d.longitude,
                             d.city_id
                             FROM doru.users u
                JOIN drugstores d on u.id = d.drugstore_id
                WHERE u.id = ?""", id) { rs: ResultSet, _: Int ->
                return@queryForObject DrugstoreProfile(
                        name = rs.getString("name"),
                        drugstoreName = rs.getString("drugstore_name"),
                        phoneNumber = rs.getString("phone_number"),
                        address = rs.getString("address"),
                        drugstorePhotoPath = rs.getString("drugstore_photo_path"),
                        latitude = rs.getDouble("latitude"),
                        longitude = rs.getDouble("longitude"),
                        cityId = rs.getInt("city_id")
                )
            }
        } catch (e: EmptyResultDataAccessException) {
            null
        }
    }

    fun getWorkDaysByDrugstoreId(id: Int): List<WorkDay> {
        return jdbcTemplate.query("SELECT work_day,time_start,time_end FROM drugstores_work_days WHERE drugstore_id = ? ", id) { rs: ResultSet, _: Int ->
            return@query WorkDay(
                    day = Day.valueOf(rs.getString("work_day")),
                    from = rs.getString("time_start"),
                    until = rs.getString("time_end")
            )
        }
    }

    fun getFileNameById(drugstoreId: Int): String {
        return jdbcTemplate.queryForObject("SELECT drugstore_photo_path FROM drugstores WHERE drugstore_id = ?", String::class.java, drugstoreId)
    }

    fun updateDrugstoreInfo(update: UpdateDrugstoreInfo, drugstoreId: Int) {
        jdbcTemplate.update("""update drugstores d
                          JOIN doru.users du on d.drugstore_id = du.id
                          SET du.name          = ?,
                              du.phone_number  = ?,
                              d.drugstore_name = ?,
                              d.address        = ?,
                              d.longitude      = ?,
                              d.latitude       = ?,
                              d.city_id        = ?
                          where du.id = ?""", update.name, update.phoneNumber, update.drugstoreName, update.address, update.longitude, update.latitude, update.cityId, drugstoreId)
    }

    @Throws(PasswordNotMatch::class)
    fun changeSelfPassword(passwordEditRequest: PasswordEditRequest, drugstoreId: Int): Boolean {
        val oldPassword = jdbcTemplate.queryForObject("SELECT password FROM users WHERE id = ?", String::class.java, drugstoreId)
        return if (passwordEncoder.matches(passwordEditRequest.password, oldPassword)) {
            jdbcTemplate.update("UPDATE users SET password = ? WHERE id = ?",
                    passwordEncoder.encode(passwordEditRequest.newPassword), drugstoreId)
            true
        } else {
            false
        }
    }

    fun getDrugstoreInfo(drugstoreId: Int): DrugstoresProfile? {
        return jdbcTemplate.queryForObject(
                """SELECT   drugstore_name,
                                           address,
                                           drugstore_photo_path,
                                           latitude,
                                           longitude,
                                           c.name,
                                           u.email,
                                           u.phone_number
                                   FROM drugstores d
                                    JOIN cities c on d.city_id = c.id
                                    JOIN users u on d.drugstore_id = u.id
                                   WHERE drugstore_id = ?""", drugstoreId) { rs, _ ->
            return@queryForObject DrugstoresProfile(
                    rs.getString("drugstore_name"),
                    rs.getString("address"),
                    rs.getString("drugstore_photo_path"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone_number"),
                    false
            )

        }
    }

    fun getDrugstoreWorkDayAndWorkTime(drugstoreId: Int): List<DrugstoreWorkDay> {
        return jdbcTemplate.query(
                """SELECT time_start,
                                         time_end,
                                  GROUP_CONCAT(work_day SEPARATOR ',') days
                                  FROM drugstores_work_days
                                  WHERE drugstore_id=?
                                  GROUP BY time_start, time_end""", drugstoreId) { rs, _ ->
            return@query DrugstoreWorkDay(
                    workDay = rs.getString("days").split(','),
                    timeStart = rs.getString("time_start"),
                    timeEnd = rs.getString("time_end")
            )
        }
    }

    fun getDrugstoreWorkDayAndTime(drugstoreId: Int): List<DrugstoreWorkDays> {
        return jdbcTemplate.query(
                """SELECT time_start,
                                         time_end,
                                  GROUP_CONCAT(work_day SEPARATOR ',') days
                                  FROM drugstores_work_days
                                  WHERE drugstore_id=?
                                  GROUP BY time_start, time_end""", drugstoreId) { rs, _ ->
            return@query DrugstoreWorkDays(
                    workDay = rs.getString("days").split(','),
                    timeStart = rs.getString("time_start"),
                    timeEnd = rs.getString("time_end")
            )
        }
    }


    fun addDrugToDrugstore(drugstoreId: Int, drug: DrugInDrugstoreRequest) {
        jdbcTemplate.update("INSERT INTO drugstore_drugs(drugstore_id, drugs_id, price, existence) VALUES (?, ?, ?, ?)",
                drugstoreId, drug.drugId, drug.price, drug.existence)
    }

    fun updateDrugInfoInDrugstore(drugstoreId: Int, drug: DrugInDrugstoreRequest) {
        jdbcTemplate.update("UPDATE drugstore_drugs SET price=?, existence=? WHERE drugstore_id=? AND drugs_id=?",
                drug.price, drug.existence, drugstoreId, drug.drugId)
    }

    fun delete(id: Int) {
        jdbcTemplate.update("DELETE FROM discounts WHERE drugstore_id=?", id)
        jdbcTemplate.update("DELETE FROM drugstore_drugs WHERE drugstore_id=?", id)
        jdbcTemplate.update("DELETE FROM drugstores_work_days WHERE drugstore_id=?", id)
        jdbcTemplate.update("DELETE FROM drugstores WHERE drugstore_id=?", id)
        jdbcTemplate.update("DELETE FROM users WHERE id=?", id)
    }

    fun searchDrugstore(name: Array<String>, search: String): List<Drugstore> {
        return jdbcTemplate.query("""SELECT dd.drugstore_id,dd.drugstore_name,dd.address,dd.latitude,dd.longitude,u.phone_number
                                        FROM drugstores dd
                                        JOIN doru.users u on dd.drugstore_id = u.id
                                        WHERE LOWER (drugstore_name) LIKE $search OR LOWER(address) LIKE $search
                                        GROUP BY dd.drugstore_id """, name) { rs: ResultSet, _ ->
            return@query Drugstore(
                    drugstoreId = rs.getInt("drugstore_id"),
                    name = rs.getString("drugstore_name"),
                    address = rs.getString("address"),
                    latitude = rs.getDouble("latitude"),
                    longitude = rs.getDouble("longitude"),
                    phoneNumber = rs.getString("phone_number"),
                    workDays = null,
                    weekends = null
            )
        }
    }

    fun getAllDrugsFromOneDrugstore(drugId: Array<Int>, drugstoreId: Int, quant: String): List<DrugstoreDrug> {
        if (drugstoreId == 0) {
            return jdbcTemplate.query("""SELECT DISTINCT u.phone_number,d.drugstore_id,d.drugstore_name,d.latitude,d.longitude,d.address
                                    FROM drugstore_drugs dd
                                          JOIN users u on dd.drugstore_id = u.id
                                          JOIN drugstores d on dd.drugstore_id = d.drugstore_id
                                    WHERE drugs_id IN ($quant)
                                    GROUP BY drugstore_id
                                    HAVING count(*) = ${drugId.size}
                                    ORDER BY sum(price)
                    """, drugId) { rs: ResultSet, _: Int ->
                return@query DrugstoreDrug(
                        drugstoreId = rs.getInt("drugstore_id"),
                        name = rs.getString("drugstore_name"),
                        address = rs.getString("address"),
                        longitude = rs.getDouble("longitude"),
                        latitude = rs.getDouble("latitude"),
                        phoneNumber = rs.getString("phone_number"),
                        workDays = null,
                        weekends = null,
                        drugsInfo = null
                )
            }
        } else {
            val ar = drugsIdAndDrugstoreIdInOneArray(drugId, drugstoreId)
            return jdbcTemplate.query("""SELECT u.phone_number,d.drugstore_id,d.drugstore_name,d.latitude,d.longitude,d.address
                                    FROM drugstore_drugs dd
                                          JOIN users u on dd.drugstore_id = u.id
                                          JOIN drugstores d on dd.drugstore_id = d.drugstore_id
                                    WHERE drugs_id IN ($quant) and dd.drugstore_id = ?
                                   GROUP BY dd.drugstore_id
                    """, ar) { rs: ResultSet, _: Int ->
                return@query DrugstoreDrug(
                        drugstoreId = rs.getInt("drugstore_id"),
                        name = rs.getString("drugstore_name"),
                        address = rs.getString("address"),
                        longitude = rs.getDouble("longitude"),
                        latitude = rs.getDouble("latitude"),
                        phoneNumber = rs.getString("phone_number"),
                        workDays = null,
                        weekends = null,
                        drugsInfo = null
                )
            }
        }
    }

    fun getAllFromOne(drugId: Array<Int>, drugstoreId: Int, quant: String): List<DrugInfo> {
        val array = drugsIdAndDrugstoreIdInOneArray(drugId, drugstoreId)
        return jdbcTemplate.query("""SELECT d.id, dd.drugstore_id, d.name,d.description,dd.price,c.name country
                                FROM drugstore_drugs dd
                                       JOIN drugs d on dd.drugs_id = d.id
                                       JOIN country c on d.country_id = c.id
                                WHERE dd.drugs_id IN ($quant)
                                  AND dd.drugstore_id = ?
                                """, array) { rs: ResultSet, _ ->
            return@query DrugInfo(
                    id = rs.getInt("id"),
                    name = rs.getString("name"),
                    description = rs.getString("description"),
                    price = rs.getDouble("price"),
                    countryName = rs.getString("country")
            )
        }
    }

    fun drugsIdAndDrugstoreIdInOneArray(drugId: Array<Int>, drugstoreId: Int): Array<Int> {
        val size = drugId.size
        val ar = Array(size + 1) { 0 }
        var count = 0
        drugId.forEach { ar[count] = it;count += 1 }
        ar[count] = drugstoreId
        return ar
    }
}




