package com.harems.api.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "billing")
public class BillingProperties {
    private String provider = "";

    public boolean isPayPal() {
        return "PAYPAL".equalsIgnoreCase(provider);
    }
}
