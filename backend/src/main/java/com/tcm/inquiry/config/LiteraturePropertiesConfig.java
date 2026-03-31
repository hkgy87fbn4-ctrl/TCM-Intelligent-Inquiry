package com.tcm.inquiry.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.tcm.inquiry.modules.literature.LiteratureProperties;

@Configuration
@EnableConfigurationProperties(LiteratureProperties.class)
public class LiteraturePropertiesConfig {}
