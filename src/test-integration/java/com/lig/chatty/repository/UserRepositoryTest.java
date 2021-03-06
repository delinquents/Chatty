package com.lig.chatty.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lig.chatty.domain.Authority;

import com.lig.chatty.domain.User;
import com.lig.chatty.repository.common.DataJpaAuditConfig;
import com.lig.chatty.repository.common.EntityFactory;

import lombok.NonNull;
import org.apache.commons.lang.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.QuerydslJpaRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE;

@TestPropertySource(properties = {"spring.batch.job.enabled=false"})
@ExtendWith(SpringExtension.class)
@DataJpaTest(includeFilters = @ComponentScan.Filter(type = ASSIGNABLE_TYPE, classes = {DataJpaAuditConfig.class}))
@ActiveProfiles({"springDataJpa", "UserRepositoryTest"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {


    public static final String OPERATION_NOT_SUPPORTED = "Operation not supported";
    public final UserRepository repository;
    private final TestEntityManager em;
    private final EntityManager entityManager;
    private final EntityFactory<User> entityFactoryUser;

    @Autowired
    public UserRepositoryTest(@NonNull UserRepository repository, @NonNull TestEntityManager em, @NonNull EntityFactory<User> entityFactoryUser, @NonNull EntityManager entityManager) {
        this.repository = repository;
        this.em = em;
        this.entityFactoryUser = entityFactoryUser;
        this.entityManager = entityManager;
    }

    @Test
    public void testRepositoryInterfaceImplementationAutowiring() {
        assertThat(repository instanceof JpaRepository
                || AopUtils.getTargetClass(repository).equals(JpaRepository.class)
        ).isTrue();
    }

    @Test
    @Transactional
    public void saveAndQueryTest() {
        final User entity = entityFactoryUser.getNewEntityInstance();
        String id = entity.getId();
        Integer version = entity.getVersion();

        final User deepEntityCopy = (User) SerializationUtils.clone(entity);

        final User entitySaved = repository.saveAndFlush(entity);
        final User entityQueried = repository.findById(id).orElse(null);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        deepEntityCopy.setVersion(0);

        deepEntityCopy.setCreatedBy(entitySaved.getCreatedBy());
        deepEntityCopy.setCreatedDate(entitySaved.getCreatedDate());

        deepEntityCopy.setUpdatedDate(entitySaved.getUpdatedDate());
        deepEntityCopy.setLastUpdBy(entitySaved.getLastUpdBy());
        assertAll(
                () -> assertThat(deepEntityCopy).isEqualToComparingFieldByFieldRecursively(entitySaved),
                () -> assertThat(deepEntityCopy).isEqualToComparingFieldByFieldRecursively(entityQueried),
                () -> assertThat(gson.toJson(deepEntityCopy).toString()).isEqualTo(gson.toJson(entitySaved).toString()),
                () -> assertThat(gson.toJson(deepEntityCopy).toString()).isEqualTo(gson.toJson(entityQueried).toString())
        );
    }

    @Test
    @Transactional
    public void updateAndQueryTest() {
        final User entitySaved = repository.saveAndFlush(entityFactoryUser.getNewEntityInstance());
        final User deepEntitySavedCopy = (User) SerializationUtils.clone(entitySaved);
        entitySaved.setName("test-update-name");

        final User entityUpdated = repository.saveAndFlush(entitySaved);

        deepEntitySavedCopy.setName(entitySaved.getName());

        deepEntitySavedCopy.setVersion(deepEntitySavedCopy.getVersion() + 1);

        deepEntitySavedCopy.setCreatedBy(deepEntitySavedCopy.getCreatedBy());
        deepEntitySavedCopy.setCreatedDate(deepEntitySavedCopy.getCreatedDate());

        deepEntitySavedCopy.setUpdatedDate(entityUpdated.getUpdatedDate());
        deepEntitySavedCopy.setLastUpdBy(entityUpdated.getLastUpdBy());
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        assertAll(
                () -> assertThat(deepEntitySavedCopy).isEqualToComparingFieldByField(entityUpdated),
                () -> assertThat(gson.toJson(deepEntitySavedCopy).toString()).isEqualTo(gson.toJson(entityUpdated).toString())
        );
    }

    @Test
    void findByEmail() {
        final User entitySaved1 = repository.saveAndFlush(entityFactoryUser.getNewEntityInstance());
        final User entitySaved2 = repository.saveAndFlush(entityFactoryUser.getNewEntityInstance());
        Optional<User> findByEmailUser = repository.findByEmail(entitySaved1.getEmail());

        assertAll(
                () -> assertThat(findByEmailUser.orElse(null)).isEqualTo(entitySaved1)
        );
    }

    @Profile("UserRepositoryTest")
    @TestConfiguration
    public static class IntegrationTestConfiguration {


        @Bean
        public EntityFactory<User> getNewEntityInstance(TestEntityManager em) {
            return new EntityFactory<User>() {
                @Override
                @Transactional(propagation = Propagation.REQUIRES_NEW)
                public User getNewEntityInstance() {
                    String userName = "test-user-name" + UUID.randomUUID().toString().replaceAll("-", "");
                    User userNew = new User();
                    userNew.setName(userName);
                    userNew.setEmail(userName + "@chatty.com");
                    userNew.setProvider(Authority.AuthProvider.local);
                    final User user = em.persistFlushFind(userNew);

                    String userName2 = "test-user-name" + UUID.randomUUID().toString().replaceAll("-", "");
                    User userNew2 = new User();
                    userNew2.setName(userName2);
                    userNew2.setEmail(userName2 + "@chatty.com");
                    userNew2.setProvider(Authority.AuthProvider.local);

                    userNew2.setLastUpdBy(user);

                    return userNew2;
                }
            };
        }
    }
}