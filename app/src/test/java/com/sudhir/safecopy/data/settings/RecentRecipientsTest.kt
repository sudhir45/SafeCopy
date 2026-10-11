package com.sudhir.safecopy.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentRecipientsTest {
    @Test
    fun newestFirstAndDeduplicatedIgnoringCase() {
        var list = emptyList<String>()
        list = RecentRecipients.add(list, "Acme Bank")
        list = RecentRecipients.add(list, "Hotel Sea View")
        list = RecentRecipients.add(list, "  acme   bank ")
        assertThat(list).containsExactly("acme bank", "Hotel Sea View").inOrder()
    }

    @Test
    fun keepsOnlyTen() {
        var list = emptyList<String>()
        for (i in 1..15) list = RecentRecipients.add(list, "R$i")
        assertThat(list).hasSize(10)
        assertThat(list.first()).isEqualTo("R15")
        assertThat(list.last()).isEqualTo("R6")
    }

    @Test
    fun blankIsIgnored() {
        assertThat(RecentRecipients.add(listOf("A"), "   ")).containsExactly("A")
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val list = listOf("Acme Bank", "Mr. Rao, Flat 4", "होटल")
        assertThat(RecentRecipients.decode(RecentRecipients.encode(list))).isEqualTo(list)
        assertThat(RecentRecipients.decode(null)).isEmpty()
        assertThat(RecentRecipients.decode("")).isEmpty()
    }

    @Test
    fun suggestMatchesAnywhereAndHidesExactMatch() {
        val list = listOf("Acme Bank", "Hotel Acme", "Rao")
        assertThat(RecentRecipients.suggest(list, "acme")).containsExactly("Acme Bank", "Hotel Acme").inOrder()
        assertThat(RecentRecipients.suggest(list, "Rao")).isEmpty()
        assertThat(RecentRecipients.suggest(list, "")).isEqualTo(list)
    }
}
