package me.iroom.sovereignty.area

import me.iroom.sovereignty.team.TeamManager
import me.iroom.sovereignty.team.TeamManager.getLeftPoint
import me.iroom.sovereignty.team.TeamManager.getTeam
import me.iroom.sovereignty.util.Option
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.*
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import java.util.*
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType


class Area(areaID : Int, coreLoc : Location, team: String) {
    constructor(areaID: Int): this(areaID, Location(Bukkit.getWorlds().first(), 0.0, 0.0, 0.0), "None")
    val areaID: Int //구역 ID <- 내부적으로는 0부터 시작되는 배열로 처리하고, 이거는 표시되는 ID

    var coreLoc: Location //코어의 좌표
    var team: String //Team ID
    var reinforced: Boolean = false //강화(무적 시간) 여부
    var reinforceEndTime = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")) //강화 풀리는 시간
    var vulnerable: Boolean = false //강화 풀린 후 취약 여부
    var vulnerableEndTime = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")) //취약 풀리는 시간

    var bar = Bukkit.createBossBar("${areaID}번 구역", BarColor.RED, BarStyle.SOLID)

    var coreHp = 50
    var maxCoreHp = 50

    var level = 2 //현재레벨

    companion object {
        const val maxLevel = 6

        fun Option.getArea() = Area(this["areaID"].int).apply {
            val opt = this@getArea
            coreLoc = Location(
                Bukkit.getWorld(UUID.fromString(opt["coreLocUID"].string)),
                opt["coreX"].double,
                opt["coreY"].double,
                opt["coreZ"].double
            )
            team = opt["team"].string
            reinforced = opt["reinforced"].string == "1"
            reinforceEndTime = Calendar.getInstance().apply { timeInMillis = opt["reinforcedTime"].long }
            vulnerable = opt["vulnerable"].string == "1"
            vulnerableEndTime = Calendar.getInstance().apply { timeInMillis = opt["vulnerableTime"].long }
            coreHp = opt["corehp"].int
            level = opt["level"].int
        }
    }

    init {
        this.areaID = areaID
        this.coreLoc = coreLoc
        this.team = team
    }

    fun levelUp() {
        if(team.getTeam()!!.getLeftPoint <= 0) return
        if (level == maxLevel) return
        if (reinforced) return
        level++
    }

    fun levelDown() {
        if (level == 0) return
        if (reinforced) return
        level--
    }

    fun giveEffect(p: Player) {
        if (p.location.world != Bukkit.getWorld("world")) return

        if (p.getTeam() != null && p.getTeam()?.name == team) {
            if (1 <= level)
                p.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 30, 0))
            if (2 <= level) {
                p.addPotionEffect(PotionEffect(PotionEffectType.LUCK, 30, 0))
                p.addPotionEffect(PotionEffect(PotionEffectType.WATER_BREATHING, 30, 0))
                p.addPotionEffect(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30, 0))
            }
            if (3 <= level) {
                p.addPotionEffect(PotionEffect(PotionEffectType.INCREASE_DAMAGE, 30, 0))
                p.addPotionEffect(PotionEffect(PotionEffectType.FAST_DIGGING, 30, 0))
            }
            if (4 <= level)
                p.addPotionEffect(PotionEffect(PotionEffectType.INCREASE_DAMAGE, 30, 1))
        } else {
            p.addPotionEffect(PotionEffect(PotionEffectType.SLOW_DIGGING, 30, 1))
            if (4 <= level)
                p.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 30, 0))
            if (5 <= level) {
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOW, 30, 0))
                p.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 30, 0))
            }
            if (6 <= level) {
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOW_DIGGING, 30, 2))
            }
        }
    }

    fun reinforceArea() {
        reinforced = true
        coreHp = 100
        maxCoreHp = 100
    }

    fun vulnerateArea() {
        reinforced = false
        vulnerable = true
        coreHp = 20
        maxCoreHp = 20
    }

    fun normalizeArea() {
        vulnerable = false
        coreHp = 50
        maxCoreHp = 50
    }

    fun coreBreak(p: Player) {
        if (p.getTeam()!!.name != team) {
            if (!reinforced) {
                if (vulnerable) {
                    while(level < 2) {
                        var select: Area? = null
                        for(x in AreaManager.Areas.filter { it.areaID != this.areaID && it.team == this.team }) {
                            select = select ?: x
                            if(select.level < x.level)
                                select = x
                        }
                        level++
                        select!!.level--

                        Bukkit.getOnlinePlayers().forEach {
                            it.sendTitle("${areaID}번 코어가 점령되었습니다!", "", 20, 40, 20)
                            it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_DEATH, 1f, 1f)
                        }
                    }

                    team = p.getTeam()!!.name
                    level = 2
                    normalizeArea()
                } else {
                    reinforceArea()
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    if (hour in 0..3) cal.add(Calendar.HOUR_OF_DAY, 8)
                    if (hour in 5..7) cal.add(Calendar.HOUR_OF_DAY, 6)
                    if (hour in 8..11) cal.add(Calendar.HOUR_OF_DAY, 4)
                    if (hour in 12..16) cal.add(Calendar.HOUR_OF_DAY, 3)
                    if (hour in 17..21) cal.add(Calendar.HOUR_OF_DAY, 2)
                    if (hour in 22..23) cal.add(Calendar.HOUR_OF_DAY, 10)
                    reinforceEndTime = cal

                    Bukkit.getOnlinePlayers().forEach {
                        it.sendTitle("${areaID}번 코어가 강화되었습니다!", "", 20, 40, 20)
                        it.playSound(it.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f)
                    }
                }

            }
        }
    }

    fun updateHpBar() {
        if(reinforced) {
            bar.setTitle("${areaID}번 구역 : 강화")
            bar.color = BarColor.RED
            bar.progress = 1.0
        }
        else if(vulnerable) {
            bar.setTitle("${areaID}번 구역 : 취약")
            bar.color = BarColor.PURPLE
            bar.progress = coreHp/20.0
        }
        else { //일반 상태
            bar.setTitle("${areaID}번 구역")
            bar.color =BarColor.GREEN
            bar.progress = coreHp/50.0
        }
    }

    val serialize get() = Option("area", "")
        .append("areaID", areaID)
        .append("coreLocUID", coreLoc.world!!.uid)
        .append("coreX", coreLoc.x)
        .append("coreY", coreLoc.y)
        .append("coreZ", coreLoc.z)
        .append("team", team)
        .append("reinforced", if(reinforced) 1 else 0)
        .append("reinforcedTime", reinforceEndTime.timeInMillis)
        .append("vulnerable", if(vulnerable) 1 else 0)
        .append("vulnerableTime", vulnerableEndTime.timeInMillis)
        .append("corehp", coreHp)
        .append("level", level)
}
