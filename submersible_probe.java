// ===========================
// TDD for ProbeControlApplication
// ===========================
// 1. Tests (src/test/java/com/example/probe)
package com.example.probe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProbeControllerTDDTests {

    @LocalServerPort
    private int port;

    private String baseUrl;
    private TestRestTemplate rest;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/probe";
        rest = new TestRestTemplate();
    }

    @Test
    void initProbe_success() {
        InitRequest req = new InitRequest();
        req.setGridWidth(5);
        req.setGridHeight(5);
        req.setObstacles(Arrays.asList(new Point(1, 1), new Point(3, 3)));
        req.setStartX(0);
        req.setStartY(0);
        req.setDirection(Direction.NORTH);

        ResponseEntity<String> resp = rest.postForEntity(baseUrl + "/init", req, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("initialized");
    }

    @Test
    void moveProbe_andSummary() {
        // initialize
        InitRequest req = new InitRequest();
        req.setGridWidth(2);
        req.setGridHeight(2);
        req.setObstacles(Arrays.asList());
        req.setStartX(0);
        req.setStartY(0);
        req.setDirection(Direction.EAST);
        rest.postForEntity(baseUrl + "/init", req, String.class);

        // execute commands F, R, F
        CommandRequest cmd = new CommandRequest();
        cmd.setCommands(Arrays.asList('F','R','F'));
        rest.postForEntity(baseUrl + "/commands", cmd, String.class);

        // summary
        ResponseEntity<SummaryResponse> summary = rest.getForEntity(baseUrl + "/summary", SummaryResponse.class);
        assertThat(summary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summary.getBody().getVisited())
            .extracting(p -> p.x + ":" + p.y)
            .containsExactly("0:0", "1:0", "1:1");
    }

    @Test
    void obstacleCollision_throws() {
        InitRequest req = new InitRequest();
        req.setGridWidth(2);
        req.setGridHeight(2);
        req.setObstacles(Arrays.asList(new Point(1, 0)));
        req.setStartX(0);
        req.setStartY(0);
        req.setDirection(Direction.EAST);
        rest.postForEntity(baseUrl + "/init", req, String.class);

        CommandRequest cmd = new CommandRequest();
        cmd.setCommands(Arrays.asList('F'));
        ResponseEntity<String> resp = rest.postForEntity(baseUrl + "/commands", cmd, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).contains("Obstacle at (1,0)");
    }

    @Test
    void outOfBounds_throws() {
        InitRequest req = new InitRequest();
        req.setGridWidth(1);
        req.setGridHeight(1);
        req.setObstacles(Arrays.asList());
        req.setStartX(0);
        req.setStartY(0);
        req.setDirection(Direction.SOUTH);
        rest.postForEntity(baseUrl + "/init", req, String.class);

        CommandRequest cmd = new CommandRequest();
        cmd.setCommands(Arrays.asList('F'));
        ResponseEntity<String> resp = rest.postForEntity(baseUrl + "/commands", cmd, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).contains("Out of bounds");
    }
}



// 2. Implementation (src/main/java/com/example/probe)
package com.example.probe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProbeControlApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProbeControlApplication.class, args);
    }
}

// --- Domain ---
class Point {
    public int x, y;
    public Point() {}
    public Point(int x, int y) { this.x = x; this.y = y; }
}

enum Direction { NORTH, EAST, SOUTH, WEST }

// --- DTOs ---
class InitRequest {
    private int gridWidth, gridHeight, startX, startY;
    private Direction direction;
    private java.util.List<Point> obstacles;
    // getters/setters omitted for brevity
}

class CommandRequest {
    private java.util.List<Character> commands;
    // getters/setters omitted
}

class SummaryResponse {
    private java.util.List<Point> visited;
    public SummaryResponse(java.util.List<Point> visited) { this.visited = visited; }
    public java.util.List<Point> getVisited() { return visited; }
}

// --- Exception ---
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.BAD_REQUEST)
class ProbeException extends RuntimeException {
    public ProbeException(String msg) { super(msg); }
}

// --- Service ---
import org.springframework.stereotype.Service;
@Service
class ProbeService {
    private int width, height;
    private java.util.Set<String> obstacles;
    private Probe probe;

    public void initialize(int w, int h, java.util.List<Point> obs, int sx, int sy, Direction dir) {
        width = w; height = h;
        obstacles = new java.util.HashSet<>();
        obs.forEach(o -> obstacles.add(o.x + ":" + o.y));
        if (obstacles.contains(sx + ":" + sy)) throw new ProbeException("Starting position on obstacle");
        probe = new Probe(sx, sy, dir);
    }

    public void executeCommands(java.util.List<Character> cmds) {
        cmds.forEach(c -> {
            switch (c) {
                case 'F': move(1); break;
                case 'B': move(-1); break;
                case 'L': turn(-1); break;
                case 'R': turn(1); break;
                default: throw new ProbeException("Invalid command: " + c);
            }
        });
    }

    private void move(int step) {
        int x = probe.position.x, y = probe.position.y;
        switch (probe.direction) {
            case NORTH: y += step; break;
            case SOUTH: y -= step; break;
            case EAST:  x += step; break;
            case WEST:  x -= step; break;
        }
        if (x < 0 || x > width || y < 0 || y > height) throw new ProbeException("Out of bounds: ("+x+","+y+")");
        if (obstacles.contains(x+":"+y)) throw new ProbeException("Obstacle at ("+x+","+y+")");
        probe.position = new Point(x,y);
        probe.visited.add(new Point(x,y));
    }

    private void turn(int dir) {
        int idx = (probe.direction.ordinal() + (dir==1?1:3)) % 4;
        probe.direction = Direction.values()[idx];
    }

    public SummaryResponse getSummary() {
        return new SummaryResponse(probe.visited);
    }
}

// --- Controller ---
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/probe")
class ProbeController {
    private final ProbeService svc;
    public ProbeController(ProbeService svc) { this.svc = svc; }

    @PostMapping("/init")
    public ResponseEntity<String> init(@RequestBody InitRequest r) {
        svc.initialize(r.getGridWidth(), r.getGridHeight(), r.getObstacles(), r.getStartX(), r.getStartY(), r.getDirection());
        return ResponseEntity.ok("Probe initialized");
    }

    @PostMapping("/commands")
    public ResponseEntity<String> commands(@RequestBody CommandRequest r) {
        svc.executeCommands(r.getCommands());
        return ResponseEntity.ok("Commands executed");
    }

    @GetMapping("/summary")
    public SummaryResponse summary() {
        return svc.getSummary();
    }
}

// --- Internal Probe ---
class Probe {
    Point position;
    Direction direction;
    java.util.List<Point> visited = new java.util.ArrayList<>();
    Probe(int x, int y, Direction d) {
        position = new Point(x,y);
        direction = d;
        visited.add(new Point(x,y));
    }
}
