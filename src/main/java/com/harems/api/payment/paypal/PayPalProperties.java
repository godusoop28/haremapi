package com.harems.api.payment.paypal;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "paypal")
public class PayPalProperties {
    private String mode = "sandbox";
    private String clientId = "";
    private String clientSecret = "";
    private String webhookId = "";
    private String productId = "";
    private String premiumPlanId = "";
    private String vipPlanId = "";
    private String trialPlanId = "";
    private String successUrl = "https://haremsweb.vercel.app/subscription/success";
    private String cancelUrl = "https://haremsweb.vercel.app/subscription/cancel";
    private String webhookUrl = "https://haremapi-3a3p.onrender.com/api/payments/paypal/webhook";

    @PostConstruct
    public void trimValues() {
        clientId = strip(clientId);
        clientSecret = strip(clientSecret);
        webhookId = strip(webhookId);
        productId = strip(productId);
        premiumPlanId = strip(premiumPlanId);
        vipPlanId = strip(vipPlanId);
        trialPlanId = strip(trialPlanId);
    }

    private static String strip(String v) {
        return v != null ? v.strip() : "";
    }

    public String getBaseUrl() {
        return "sandbox".equalsIgnoreCase(mode)
                ? "https://api-m.sandbox.paypal.com"
                : "https://api-m.paypal.com";
    }

    /** Devuelve false si productId está vacío o es un placeholder conocido. */
    public boolean isProductIdEffectivelyConfigured() {
        return !productId.isBlank()
                && !productId.toUpperCase().contains("PEGA_AQUI");
    }
}
