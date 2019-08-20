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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApplicationTests {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void contextLoads() {
    }

    @Test
    public void testCreateAndGet() {
        List<Superhero> heroes = restTemplate.exchange("/", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Superhero>>() {
                }).getBody();
        assertThat(heroes).isEmpty();

        final Superhero hero1 = restTemplate.postForObject("/new", null, Superhero.class);
        assertThat(hero1).isNotNull();
        assertThat(hero1.getName()).isNotNull();

        heroes = restTemplate.exchange("/", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Superhero>>() {
                }).getBody();
        assertThat(heroes).hasSize(1);
        assertThat(heroes).contains(hero1);

        final Superhero hero2 = restTemplate.postForObject("/new", null, Superhero.class);
        assertThat(hero2).isNotNull();
        assertThat(hero2.getName()).isNotNull();

        heroes = restTemplate.exchange("/", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Superhero>>() {
                }).getBody();
        assertThat(heroes).hasSize(2);
        assertThat(heroes).containsExactly(hero2, hero1);
    }
}
