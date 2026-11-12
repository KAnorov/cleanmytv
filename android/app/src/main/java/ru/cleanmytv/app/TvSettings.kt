package ru.cleanmytv.app

data class TvSettingsState(
    val dnsMode: String,
    val dnsSpecifier: String,
    val windowScale: String,
    val transitionScale: String,
    val animatorScale: String
) {
    val dnsBlocking: Boolean get() = dnsMode == "hostname"
    val animationsOff: Boolean
        get() = listOf(windowScale, transitionScale, animatorScale).all { it.toFloatOrNull() == 0f }
    val animationsDefault: Boolean
        get() = listOf(windowScale, transitionScale, animatorScale).all { it.toFloatOrNull() == 1f }
}

/**
 * Настройки самого телевизора, которые меняются через ADB: Private DNS с блокировкой
 * рекламы и скорость системных анимаций. Обе настройки обратимы и не удаляют ничего.
 */
class TvSettings(private val tv: TvClient) {

    fun read(): TvSettingsState = TvSettingsState(
        dnsMode = tv.setting("private_dns_mode"),
        dnsSpecifier = tv.setting("private_dns_specifier"),
        windowScale = tv.setting("window_animation_scale"),
        transitionScale = tv.setting("transition_animation_scale"),
        animatorScale = tv.setting("animator_duration_scale")
    )

    fun enableDnsBlocking(): String {
        tv.putSetting("private_dns_mode", "hostname")
        tv.putSetting("private_dns_specifier", DNS_HOST)
        return "Private DNS включён: $DNS_HOST. Перезагрузите TV, чтобы настройка заработала наверняка."
    }

    fun disableDnsBlocking(): String {
        tv.putSetting("private_dns_mode", "off")
        return "Private DNS выключен: TV снова использует DNS от провайдера."
    }

    fun disableAnimations(): String {
        tv.putSetting("window_animation_scale", "0.0")
        tv.putSetting("transition_animation_scale", "0.0")
        tv.putSetting("animator_duration_scale", "0.0")
        return "Анимации выключены. Меню TV будет переключаться без задержек."
    }

    fun resetAnimations(): String {
        tv.putSetting("window_animation_scale", "1.0")
        tv.putSetting("transition_animation_scale", "1.0")
        tv.putSetting("animator_duration_scale", "1.0")
        return "Анимации возвращены к стандартной скорости (1.0)."
    }

    companion object {
        const val DNS_HOST = "dns.adguard-dns.com"
    }
}
