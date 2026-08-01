package com.luc.body.network

data class SupabaseConfig(
    val baseUrl: String,
    val publishableKey: String,
) {
    fun requireValid(): SupabaseConfig {
        require(baseUrl.startsWith("https://") && baseUrl.endsWith(".supabase.co"))
        require(publishableKey.startsWith("sb_publishable_"))
        return this
    }
}
