package com.wjiamao.belt_monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.wjiamao.belt_monitor")
public class BeltMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BeltMonitorApplication.class, args);
    }
}