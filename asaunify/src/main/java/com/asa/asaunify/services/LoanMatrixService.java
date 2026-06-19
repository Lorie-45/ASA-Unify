package com.asa.asaunify.services;



import com.asa.asaunify.entity.Request;
import com.asa.asaunify.enums.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class LoanMatrixService {

    // MSME Silver range
    private static final BigDecimal MSME_SILVER_MIN =
            new BigDecimal("2000001");
    private static final BigDecimal MSME_SILVER_MAX =
            new BigDecimal("5000000");

    // MSME Gold range
    private static final BigDecimal MSME_GOLD_MIN =
            new BigDecimal("5000001");
    private static final BigDecimal MSME_GOLD_MAX =
            new BigDecimal("10000000");

    // MSME Silver max top-up increment
    private static final BigDecimal MSME_SILVER_MAX_INCREMENT =
            new BigDecimal("1500000");

    // All MSME loans (Silver and Gold) share the same three approvers
    private static final List<Role> MSME_APPROVERS = List.of(
            Role.MSME_OFFICER,
            Role.RM,
            Role.CREDIT_OFFICER
    );

    public List<Role> resolveApprovers(Request request) {
        // Extract amount from extraFields JSONB
        Object amountObj = request.getExtraField("amount");
        if (amountObj == null) {
            throw new IllegalArgumentException(
                    "Loan request is missing amount in extraFields"
            );
        }

        BigDecimal amount = new BigDecimal(amountObj.toString());

        // Validate amount is within allowed range
        validateAmount(amount, request);

        // Both MSME Silver and Gold use the same approvers
        // per the final loan matrix
        log.info("Loan amount {} resolved to approvers: {}",
                amount, MSME_APPROVERS);

        return MSME_APPROVERS;
    }

    // Validate the loan amount is within allowed bounds
    private void validateAmount(BigDecimal amount, Request request) {
        Boolean isTopUp = (Boolean) request.getExtraField("is_top_up");

        if (Boolean.TRUE.equals(isTopUp)) {
            // Top-up validation — only MSME Silver supports top-ups
            // Maximum increment is 1,500,000 RWF
            if (amount.compareTo(MSME_SILVER_MAX_INCREMENT) > 0) {
                throw new IllegalArgumentException(
                        "Top-up amount exceeds maximum increment of 1,500,000 RWF"
                );
            }
        } else {
            // First cycle validation
            boolean inSilverRange =
                    amount.compareTo(MSME_SILVER_MIN) >= 0 &&
                            amount.compareTo(MSME_SILVER_MAX) <= 0;

            boolean inGoldRange =
                    amount.compareTo(MSME_GOLD_MIN) >= 0 &&
                            amount.compareTo(MSME_GOLD_MAX) <= 0;

            if (!inSilverRange && !inGoldRange) {
                throw new IllegalArgumentException(
                        "Loan amount " + amount +
                                " RWF is outside allowed range. " +
                                "MSME Silver: 2,000,001 - 5,000,000 RWF. " +
                                "MSME Gold: 5,000,001 - 10,000,000 RWF."
                );
            }

            // MSME Gold does not support top-ups — validated in service layer
        }
    }

    // Returns the loan type label based on amount
    // Used for display purposes in reports and notifications
    public String resolveLoanType(BigDecimal amount, boolean isTopUp) {
        if (isTopUp) return "MSME_SILVER_TOP_UP";

        if (amount.compareTo(MSME_SILVER_MIN) >= 0 &&
                amount.compareTo(MSME_SILVER_MAX) <= 0) {
            return "MSME_SILVER";
        }

        if (amount.compareTo(MSME_GOLD_MIN) >= 0 &&
                amount.compareTo(MSME_GOLD_MAX) <= 0) {
            return "MSME_GOLD";
        }

        return "UNKNOWN";
    }
}