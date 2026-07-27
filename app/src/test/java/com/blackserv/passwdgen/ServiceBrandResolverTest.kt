package com.blackserv.passwdgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceBrandResolverTest {
    @Test
    fun detectsGoogleFromSubdomainWithoutScheme() {
        assertEquals(
            ServiceBrandId.GOOGLE,
            ServiceBrandResolver.resolve(service = "Konto", website = "accounts.google.com/login"),
        )
    }

    @Test
    fun detectsMicrosoftFromOutlookHost() {
        assertEquals(
            ServiceBrandId.MICROSOFT,
            ServiceBrandResolver.resolve(service = "Poczta", website = "https://outlook.live.com"),
        )
    }

    @Test
    fun detectsAmazonFromCountryDomain() {
        assertEquals(
            ServiceBrandId.AMAZON,
            ServiceBrandResolver.resolve(service = "Sklep", website = "amazon.co.uk"),
        )
    }

    @Test
    fun detectsProtonFromServiceAlias() {
        assertEquals(
            ServiceBrandId.PROTON,
            ServiceBrandResolver.resolve(service = "Proton VPN", website = ""),
        )
    }

    @Test
    fun stripsCommonWebsitePrefix() {
        assertEquals("github.com", ServiceBrandResolver.normalizeHost("www.github.com/session"))
    }

    @Test
    fun unknownServiceKeepsMonogramFallback() {
        assertNull(ServiceBrandResolver.resolve(service = "Moja firma", website = "panel.example.org"))
    }
}