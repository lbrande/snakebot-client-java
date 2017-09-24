package se.cygni.snake;

import static se.cygni.snake.api.model.SnakeDirection.DOWN;
import static se.cygni.snake.api.model.SnakeDirection.LEFT;
import static se.cygni.snake.api.model.SnakeDirection.RIGHT;
import static se.cygni.snake.api.model.SnakeDirection.UP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.WebSocketSession;
import se.cygni.snake.api.event.GameEndedEvent;
import se.cygni.snake.api.event.GameLinkEvent;
import se.cygni.snake.api.event.GameResultEvent;
import se.cygni.snake.api.event.GameStartingEvent;
import se.cygni.snake.api.event.MapUpdateEvent;
import se.cygni.snake.api.event.SnakeDeadEvent;
import se.cygni.snake.api.event.TournamentEndedEvent;
import se.cygni.snake.api.exception.InvalidPlayerName;
import se.cygni.snake.api.model.GameMode;
import se.cygni.snake.api.model.GameSettings;
import se.cygni.snake.api.model.Map;
import se.cygni.snake.api.model.PlayerPoints;
import se.cygni.snake.api.model.SnakeDirection;
import se.cygni.snake.api.response.PlayerRegistered;
import se.cygni.snake.api.util.GameSettingsUtils;
import se.cygni.snake.client.AnsiPrinter;
import se.cygni.snake.client.BaseSnakeClient;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

public class SimpleSnakePlayer extends BaseSnakeClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSnakePlayer.class);

  // Set to false if you want to start the game from a GUI
  private static final boolean AUTO_START_GAME = false;

  // Personalise your game ...
  private static final String SERVER_NAME = "snake.cygni.se";
  private static final int SERVER_PORT = 80;

  private static final GameMode GAME_MODE = GameMode.TRAINING;
  private static final String SNAKE_NAME = "The Simple Snake";

  // Set to false if you don't want the game world printed every game tick.
  private static final boolean ANSI_PRINTER_ACTIVE = false;
  private AnsiPrinter ansiPrinter = new AnsiPrinter(ANSI_PRINTER_ACTIVE, true);

  private Random random = new Random();
  private SnakeDirection currentDirection = SnakeDirection.values()[random.nextInt(4)];
  private int turnDirection = -1;

  public static void main(String[] args) {
    SimpleSnakePlayer simpleSnakePlayer = new SimpleSnakePlayer();

    try {
      ListenableFuture<WebSocketSession> connect = simpleSnakePlayer.connect();
      connect.get();
    } catch (Exception e) {
      LOGGER.error("Failed to connect to server", e);
      System.exit(1);
    }

    startTheSnake(simpleSnakePlayer);
  }

  /**
   * The Snake client will continue to run ... : in TRAINING mode, until the single game ends. : in
   * TOURNAMENT mode, until the server tells us its all over.
   */
  private static void startTheSnake(final SimpleSnakePlayer simpleSnakePlayer) {
    Runnable task =
        () -> {
          do {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          } while (simpleSnakePlayer.isPlaying());

          LOGGER.info("Shutting down");
        };

    Thread thread = new Thread(task);
    thread.start();
  }

  @Override
  public void onMapUpdate(MapUpdateEvent mapUpdateEvent) {
    ansiPrinter.printMap(mapUpdateEvent);

    Map map = mapUpdateEvent.getMap();

    // MapUtil contains lot's of useful methods for querying the map!
    MapUtil mapUtil = new MapUtil(map, getPlayerId());

    if (!mapUtil.canIMoveInDirection(currentDirection)
        || openMovesInDirection(mapUtil, currentDirection) < 2
        || snakeHeadInDirection(map, mapUtil, currentDirection)) {
      SnakeDirection preferredDirection;
      SnakeDirection otherDirection;
      if (turnDirection == 1) {
        preferredDirection = nextDirection(currentDirection);
        otherDirection = lastDirection(currentDirection);
      } else {
        preferredDirection = lastDirection(currentDirection);
        otherDirection = nextDirection(currentDirection);
      }
      int preferredOpenMoves = openMovesInDirection(mapUtil, preferredDirection);
      int otherOpenMoves = openMovesInDirection(mapUtil, otherDirection);
      if ((preferredOpenMoves > 1 && !snakeHeadInDirection(map, mapUtil, preferredDirection))
          || (otherOpenMoves > 1 && !snakeHeadInDirection(map, mapUtil, otherDirection))) {
        if (mapUtil.canIMoveInDirection(preferredDirection)
            && (!mapUtil.canIMoveInDirection(otherDirection)
                || snakeHeadInDirection(map, mapUtil, otherDirection)
                || preferredOpenMoves >= otherOpenMoves)) {
          currentDirection = preferredDirection;
          turnDirection = -turnDirection;
        } else {
          currentDirection = otherDirection;
        }
      }
    }
    registerMove(mapUpdateEvent.getGameTick(), currentDirection);
  }

  private MapCoordinate translateInDirection(
      MapCoordinate position, SnakeDirection direction, int steps) {
    switch (direction) {
      case LEFT:
        return position.translateBy(-steps, 0);
      case RIGHT:
        return position.translateBy(steps, 0);
      case UP:
        return position.translateBy(0, -steps);
      case DOWN:
        return position.translateBy(0, steps);
      default:
    }
    throw new NullPointerException();
  }

  private int openMovesInDirection(MapUtil mapUtil, SnakeDirection direction) {
    MapCoordinate nextPosition = translateInDirection(mapUtil.getMyPosition(), direction, 1);
    ArrayList<SnakeDirection> directions = new ArrayList<>();
    directions.add(lastDirection(direction));
    directions.add(direction);
    directions.add(nextDirection(direction));
    return (int)
        directions
            .stream()
            .filter(
                (SnakeDirection currentDirection) -> {
                  MapCoordinate position = translateInDirection(nextPosition, currentDirection, 1);
                  return mapUtil.isTileAvailableForMovementTo(position);
                })
            .count();
  }

  private boolean snakeHeadInDirection(Map map, MapUtil mapUtil, SnakeDirection direction) {
    MapCoordinate nextPosition = translateInDirection(mapUtil.getMyPosition(), direction, 1);
    ArrayList<SnakeDirection> directions = new ArrayList<>();
    directions.add(lastDirection(direction));
    directions.add(direction);
    directions.add(nextDirection(direction));
    return directions
        .stream()
        .anyMatch(
            (SnakeDirection currentDirection) -> {
              MapCoordinate position = translateInDirection(nextPosition, currentDirection, 1);
              return otherSnakeHeads(map, mapUtil).contains(position);
            });
  }

  private List<MapCoordinate> otherSnakeHeads(Map map, MapUtil mapUtil) {
    return Arrays.stream(map.getSnakeInfos())
        .filter(snakeInfo -> snakeInfo.isAlive() && !snakeInfo.getId().equals(getPlayerId()))
        .map(
            snakeInfo ->
                mapUtil.translatePosition(snakeInfo.getPositions()[snakeInfo.getLength() - 1]))
        .collect(Collectors.toList());
  }

  private SnakeDirection nextDirection(SnakeDirection direction) {
    switch (direction) {
      case LEFT:
        return UP;
      case RIGHT:
        return DOWN;
      case UP:
        return RIGHT;
      case DOWN:
        return LEFT;
      default:
    }
    throw new NullPointerException();
  }

  private SnakeDirection lastDirection(SnakeDirection direction) {
    switch (direction) {
      case LEFT:
        return DOWN;
      case RIGHT:
        return UP;
      case UP:
        return LEFT;
      case DOWN:
        return RIGHT;
      default:
    }
    throw new NullPointerException();
  }

  @Override
  public void onInvalidPlayerName(InvalidPlayerName invalidPlayerName) {
    LOGGER.debug("InvalidPlayerNameEvent: " + invalidPlayerName);
  }

  @Override
  public void onSnakeDead(SnakeDeadEvent snakeDeadEvent) {
    LOGGER.info(
        "A snake {} died by {}", snakeDeadEvent.getPlayerId(), snakeDeadEvent.getDeathReason());
  }

  @Override
  public void onGameResult(GameResultEvent gameResultEvent) {
    LOGGER.info("Game result:");
    gameResultEvent.getPlayerRanks().forEach(playerRank -> LOGGER.info(playerRank.toString()));
  }

  @Override
  public void onGameEnded(GameEndedEvent gameEndedEvent) {
    LOGGER.debug("GameEndedEvent: " + gameEndedEvent);
  }

  @Override
  public void onGameStarting(GameStartingEvent gameStartingEvent) {
    LOGGER.debug("GameStartingEvent: " + gameStartingEvent);
  }

  @Override
  public void onPlayerRegistered(PlayerRegistered playerRegistered) {
    LOGGER.info("PlayerRegistered: " + playerRegistered);

    if (AUTO_START_GAME) {
      startGame();
    }
  }

  @Override
  public void onTournamentEnded(TournamentEndedEvent tournamentEndedEvent) {
    LOGGER.info(
        "Tournament has ended, winner playerId: {}", tournamentEndedEvent.getPlayerWinnerId());
    int c = 1;
    for (PlayerPoints pp : tournamentEndedEvent.getGameResult()) {
      LOGGER.info("{}. {} - {} points", c++, pp.getName(), pp.getPoints());
    }
  }

  @Override
  public void onGameLink(GameLinkEvent gameLinkEvent) {
    LOGGER.info("The game can be viewed at: {}", gameLinkEvent.getUrl());
  }

  @Override
  public void onSessionClosed() {
    LOGGER.info("Session closed");
  }

  @Override
  public void onConnected() {
    LOGGER.info("Connected, registering for training...");
    GameSettings gameSettings = GameSettingsUtils.defaultTournament();
    registerForGame(gameSettings);
  }

  @Override
  public String getName() {
    return SNAKE_NAME;
  }

  @Override
  public String getServerHost() {
    return SERVER_NAME;
  }

  @Override
  public int getServerPort() {
    return SERVER_PORT;
  }

  @Override
  public GameMode getGameMode() {
    return GAME_MODE;
  }
}
