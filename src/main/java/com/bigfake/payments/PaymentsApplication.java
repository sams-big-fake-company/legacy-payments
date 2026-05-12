package com.bigfake.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Legacy Payment Processing Application
 *
 * NOTE: This application was originally written in 2019 and has grown organically.
 * A rewrite to microservices is planned but not yet scheduled.
 *
 * @author payments-team
 * @since 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class PaymentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsApplication.class, args);
    }
}
