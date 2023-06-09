/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.moduliths.events.jpa;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.moduliths.events.EventSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.AbstractJpaVendorAdapter;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Oliver Drotbohm
 */
@ExtendWith(SpringExtension.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
@Transactional
@RequiredArgsConstructor
class JpaEventPublicationRepositoryIntegrationTests {

	@Configuration
	@Import(JpaEventPublicationConfiguration.class)
	static class TestConfig {

		@Bean
		EventSerializer eventSerializer() {
			return mock(EventSerializer.class);
		}

		// Database

		@Bean
		EmbeddedDatabase hsqlDatabase() {
			return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.HSQL).build();
		}

		// JPA

		@Bean
		LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {

			AbstractJpaVendorAdapter vendor = new HibernateJpaVendorAdapter();
			vendor.setGenerateDdl(true);

			LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
			factory.setJpaVendorAdapter(vendor);
			factory.setDataSource(dataSource);
			factory.setPackagesToScan(getClass().getPackage().getName());

			return factory;
		}

		@Bean
		EntityManager entityManager(EntityManagerFactory factory) {
			return SharedEntityManagerCreator.createSharedEntityManager(factory);
		}

		@Bean
		JpaTransactionManager transactionManager(EntityManagerFactory factory) {
			return new JpaTransactionManager(factory);
		}
	}

	private final JpaEventPublicationRepository repository;

	@Test
	void persistsJpaEventPublication() {

		JpaEventPublication publication = JpaEventPublication.of(Instant.now(), "listener", "", Object.class);

		// Store publication
		repository.create(publication);

		assertThat(repository.findByCompletionDateIsNull()).containsExactly(publication);
		assertThat(repository.findBySerializedEventAndListenerId("", "listener")).isPresent();

		// Complete publication
		repository.update(publication.markCompleted());

		assertThat(repository.findByCompletionDateIsNull()).isEmpty();
	}
}
