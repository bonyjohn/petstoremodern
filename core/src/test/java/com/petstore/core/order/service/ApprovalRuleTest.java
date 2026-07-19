package com.petstore.core.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Characterizes the auto-approval rule against the legacy
 * {@code PurchaseOrderMDB.canIApprove}: strictly-less-than thresholds per
 * locale, and no auto-approval at all outside en_US/ja_JP.
 */
class ApprovalRuleTest {

	@Test
	void enUsUnder500IsApproved() {
		assertThat(ApprovalService.canIApprove("en_US", new BigDecimal("499.99"))).isTrue();
	}

	@Test
	void enUsExactly500IsNotApproved() {
		assertThat(ApprovalService.canIApprove("en_US", new BigDecimal("500"))).isFalse();
		assertThat(ApprovalService.canIApprove("en_US", new BigDecimal("500.00"))).isFalse();
	}

	@Test
	void jaJpUnder50000IsApproved() {
		assertThat(ApprovalService.canIApprove("ja_JP", new BigDecimal("49999"))).isTrue();
	}

	@Test
	void jaJpExactly50000IsNotApproved() {
		assertThat(ApprovalService.canIApprove("ja_JP", new BigDecimal("50000"))).isFalse();
	}

	@Test
	void zhCnNeverAutoApprovesAtAnyAmount() {
		assertThat(ApprovalService.canIApprove("zh_CN", new BigDecimal("0.01"))).isFalse();
		assertThat(ApprovalService.canIApprove("zh_CN", new BigDecimal("100"))).isFalse();
		assertThat(ApprovalService.canIApprove("zh_CN", new BigDecimal("1000000"))).isFalse();
	}

	@Test
	void unknownLocaleNeverAutoApproves() {
		assertThat(ApprovalService.canIApprove("fr_FR", new BigDecimal("1"))).isFalse();
	}
}
