package br.com.soloibiapaba;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.TestExecutionListeners(
        listeners = org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
        inheritListeners = false)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:application-test;DB_CLOSE_DELAY=-1")
class SoloIbiapabaApplicationTest {

    @Test
    void contextLoads() {
    }
}
