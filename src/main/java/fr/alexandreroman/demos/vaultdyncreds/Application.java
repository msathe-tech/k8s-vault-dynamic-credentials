/*
 * Copyright (c) 2019 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.alexandreroman.demos.vaultdyncreds;

import com.github.javafaker.Faker;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Date;
import java.util.List;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@RestController
@RequiredArgsConstructor
@Slf4j
class IndexController {
    private final Faker faker = new Faker();
    private final SuperheroRepository repo;

    @GetMapping(value = "/")
    @Transactional(readOnly = true)
    List<Superhero> index() {
        return repo.findByOrderByCreatedDesc();
    }

    @PostMapping(value = "/new")
    @Transactional
    Superhero newInstance() {
        // Create a random superhero instance.
        final com.github.javafaker.Superhero randomHero = faker.superhero();
        Superhero hero = new Superhero();
        hero.setName(randomHero.name());
        hero.setPower(randomHero.power());
        hero = repo.save(hero);
        log.info("Created superhero: {}", hero);
        return hero;
    }
}

@Data
@Entity
class Superhero {
    @Id
    @Column(length = 128)
    private String name;
    @Column(nullable = false, length = 64)
    private String power;
    @Column(nullable = false)
    private Date created = new Date();
}

interface SuperheroRepository extends JpaRepository<Superhero, String> {
    List<Superhero> findByOrderByCreatedDesc();
}
