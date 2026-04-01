package com.tcm.inquiry.modules.agent.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.tcm.inquiry.modules.agent.entity.AgentAppConfig;
import com.tcm.inquiry.modules.agent.repository.AgentAppConfigRepository;

@Configuration
@EntityScan(basePackageClasses = AgentAppConfig.class)
@EnableJpaRepositories(basePackageClasses = AgentAppConfigRepository.class)
public class AgentJpaConfig {}
