package se.cygni.snake;

import static se.cygni.snake.api.model.SnakeDirection.DOWN;
import static se.cygni.snake.api.model.SnakeDirection.LEFT;
import static se.cygni.snake.api.model.SnakeDirection.RIGHT;
import static se.cygni.snake.api.model.SnakeDirection.UP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
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
   * The Snake client will continue to run ...
   * : in TRAINING mode, until the single game ends.
   * : in TOURNAMENT mode, until the server tells us its all over.
   */
  private static void startTheSnake(final SimpleSnakePlayer simpleSnakePlayer) {
    Runnable task = () -> {
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

    // MapUtil contains lot's of useful methods for querying the map!
    MapUtil mapUtil = new MapUtil(mapUpdateEvent.getMap(), getPlayerId());

    List<MapCoordinate> foodCoordinates = Arrays.asList(mapUtil.listCoordinatesContainingFood());
    ArrayList<MapCoordinate> lastCoordinates = new ArrayList<>();
    ArrayList<MapCoordinate> currentCoordinates = new ArrayList<>();
    ArrayList<ArrayList<SnakeDirection>> currentPaths = new ArrayList<>();
    ArrayList<SnakeDirection> correctPath = null;

    if (!foodCoordinates.isEmpty()) {
      currentCoordinates.add(mapUtil.getMyPosition());
      currentPaths.add(new ArrayList<>());

      pathLoop:
      do {
        ArrayList<MapCoordinate> newCoordinates = new ArrayList<>();
        ArrayList<ArrayList<SnakeDirection>> newPaths = new ArrayList<>();
        for (int i = 0; i < currentCoordinates.size(); i++) {
          MapCoordinate top = currentCoordinates.get(i).translateBy(0, -1);
          MapCoordinate bottom = currentCoordinates.get(i).translateBy(0, 1);
          MapCoordinate left = currentCoordinates.get(i).translateBy(-1, 0);
          MapCoordinate right = currentCoordinates.get(i).translateBy(1, 0);
          if (!lastCoordinates.contains(top) && mapUtil.isTileAvailableForMovementTo(top)) {
            newCoordinates.add(top);
            ArrayList<SnakeDirection> path = new ArrayList<>(currentPaths.get(i));
            path.add(UP);
            if (foodCoordinates.contains(top)) {
              correctPath = path;
              break pathLoop;
            }
            newPaths.add(path);
          }
          if (!lastCoordinates.contains(bottom) && mapUtil.isTileAvailableForMovementTo(bottom)) {
            newCoordinates.add(bottom);
            ArrayList<SnakeDirection> path = new ArrayList<>(currentPaths.get(i));
            path.add(DOWN);
            if (foodCoordinates.contains(bottom)) {
              correctPath = path;
              break pathLoop;
            }
            newPaths.add(path);
          }
          if (!lastCoordinates.contains(left) && mapUtil.isTileAvailableForMovementTo(left)) {
            newCoordinates.add(left);
            ArrayList<SnakeDirection> path = new ArrayList<>(currentPaths.get(i));
            path.add(LEFT);
            if (foodCoordinates.contains(left)) {
              correctPath = path;
              break pathLoop;
            }
            newPaths.add(path);
          }
          if (!lastCoordinates.contains(right) && mapUtil.isTileAvailableForMovementTo(right)) {
            newCoordinates.add(right);
            ArrayList<SnakeDirection> path = new ArrayList<>(currentPaths.get(i));
            path.add(RIGHT);
            if (foodCoordinates.contains(right)) {
              correctPath = path;
              break pathLoop;
            }
            newPaths.add(path);
          }
        }
        lastCoordinates = currentCoordinates;
        currentCoordinates = newCoordinates;
        currentPaths = newPaths;
      } while (!currentCoordinates.isEmpty());
    }

    if (correctPath == null) {
      if (!mapUtil.canIMoveInDirection(currentDirection)) {
        SnakeDirection[] possibleDirections = Arrays.stream(SnakeDirection.values()).filter(mapUtil::canIMoveInDirection).toArray(SnakeDirection[]::new);
        if (possibleDirections.length > 0) {
          currentDirection = possibleDirections[random.nextInt(possibleDirections.length)];
        } else {
          currentDirection = SnakeDirection.values()[random.nextInt(4)];
        }
      }
    } else {
      currentDirection = correctPath.get(0);
    }
    registerMove(mapUpdateEvent.getGameTick(), currentDirection);
  }


  @Override
  public void onInvalidPlayerName(InvalidPlayerName invalidPlayerName) {
    LOGGER.debug("InvalidPlayerNameEvent: " + invalidPlayerName);
  }

  @Override
  public void onSnakeDead(SnakeDeadEvent snakeDeadEvent) {
    LOGGER.info("A snake {} died by {}",
        snakeDeadEvent.getPlayerId(),
        snakeDeadEvent.getDeathReason());
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
    LOGGER.info("Tournament has ended, winner playerId: {}", tournamentEndedEvent.getPlayerWinnerId());
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
