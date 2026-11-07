package com.example.tracker.config;

import com.example.tracker.dto.BoardDtos.AddMemberRequest;
import com.example.tracker.dto.BoardDtos.CreateBoardRequest;
import com.example.tracker.dto.TaskDtos.CreateTaskRequest;
import com.example.tracker.model.AppUser;
import com.example.tracker.model.BoardRole;
import com.example.tracker.model.Role;
import com.example.tracker.model.TaskStatus;
import com.example.tracker.repository.UserRepository;
import com.example.tracker.service.BoardService;
import com.example.tracker.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Demo data for local runs and demos. Disabled by default in the prod profile. */
@Configuration
@ConditionalOnProperty(name = "app.seed.demo", havingValue = "true")
@Slf4j
public class DataSeeder {

    @Bean
    CommandLineRunner seedDemo(UserRepository users, PasswordEncoder encoder, BoardService boards, TaskService tasks,
                               JdbcTemplate jdbc, TransactionTemplate tx,
                               @Value("${app.seed.password}") String password) {
        return args -> tx.executeWithoutResult(status -> {
            // Several replicas may boot at once: a transaction-scoped advisory lock makes seeding run once.
            jdbc.query("select pg_advisory_xact_lock(727274)", rs -> {});
            if (users.existsByUsername("admin")) {
                return;
            }
            for (String[] u : new String[][]{{"admin", "ROLE_ADMIN"}, {"alice", "ROLE_MEMBER"}, {"bob", "ROLE_MEMBER"}, {"carol", "ROLE_MEMBER"}}) {
                users.save(AppUser.builder().username(u[0]).email(u[0] + "@example.com")
                        .password(encoder.encode(password)).role(Role.valueOf(u[1])).build());
            }
            var board = boards.create(new CreateBoardRequest("Launch plan", "Demo board shared by admin, alice, bob and carol"), "admin");
            boards.addMember(board.id(), new AddMemberRequest("alice", BoardRole.EDITOR), "admin");
            boards.addMember(board.id(), new AddMemberRequest("bob", BoardRole.EDITOR), "admin");
            boards.addMember(board.id(), new AddMemberRequest("carol", BoardRole.VIEWER), "admin");
            tasks.create(board.id(), new CreateTaskRequest("Write launch announcement", "Draft blog post and changelog", TaskStatus.TODO, "alice"), "admin");
            tasks.create(board.id(), new CreateTaskRequest("Load test WebSocket fan-out", "k6 scenario with 200 concurrent viewers", TaskStatus.IN_PROGRESS, "bob"), "admin");
            tasks.create(board.id(), new CreateTaskRequest("Set up CI", "Run integration tests with Testcontainers", TaskStatus.DONE, "admin"), "alice");
            log.info("Seeded demo users admin/alice/bob/carol and board '{}'", board.name());
        });
    }
}
