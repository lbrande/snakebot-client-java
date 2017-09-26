package se.cygni.snake;

import java.util.Comparator;
import java.util.HashMap;
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
import se.cygni.snake.api.model.Map;
import se.cygni.snake.api.model.PlayerPoints;
import se.cygni.snake.api.model.SnakeDirection;
import se.cygni.snake.api.response.PlayerRegistered;
import se.cygni.snake.api.util.GameSettingsUtils;
import se.cygni.snake.client.AnsiPrinter;
import se.cygni.snake.client.BaseSnakeClient;
import se.cygni.snake.client.MapCoordinate;

public class SimpleSnakePlayer extends BaseSnakeClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleSnakePlayer.class);

  // Set to false if you want to start the game from a GUI
  private static final boolean AUTO_START_GAME = false;

  // Personalise your game ...
  private static final String SERVER_NAME = "snake.cygni.se";
  private static final int SERVER_PORT = 80;

  private static final GameMode GAME_MODE = GameMode.TRAINING;
  private static final String SNAKE_NAME = "Snokas";

  // Set to false if you don't want the game world printed every game tick.
  private static final boolean ANSI_PRINTER_ACTIVE = false;
  private AnsiPrinter ansiPrinter = new AnsiPrinter(ANSI_PRINTER_ACTIVE, true);

  private Random random = new Random();
  private SnakeDirection currentDirection = SnakeDirection.values()[random.nextInt(4)];
  private boolean turnLeft = true;

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
    long startTime = System.currentTimeMillis();
    ansiPrinter.printMap(mapUpdateEvent);

    Map map = mapUpdateEvent.getMap();

    // MapUtil contains lot's of useful methods for querying the map!
    ExtendedMapUtil mapUtil = new ExtendedMapUtil(map, getPlayerId());

    SnakeDirection rightDirection =
        turnLeft ? mapUtil.lastDirection(currentDirection) : mapUtil.nextDirection(currentDirection);
    SnakeDirection wrongDirection =
        turnLeft ? mapUtil.nextDirection(currentDirection) : mapUtil.lastDirection(currentDirection);

    SnakeDirection[] directions =
        new SnakeDirection[] {currentDirection, rightDirection, wrongDirection};

    MapCoordinate center = new MapCoordinate(map.getWidth() / 2, map.getHeight() / 2);

    HashMap<SnakeDirection, List<MapCoordinate>> reachablePositions = new HashMap<>();
    reachablePositions.put(directions[0], mapUtil.reachablePositions(mapUtil.translateInDirection(mapUtil.getMyPosition(), directions[0], 1)));
    reachablePositions.put(
        directions[1],
        reachablePositions
            .get(directions[0])
            .contains(mapUtil.translateInDirection(mapUtil.getMyPosition(), directions[1], 1))
            ? reachablePositions.get(directions[0])
            : mapUtil.reachablePositions(mapUtil.translateInDirection(mapUtil.getMyPosition(), directions[1], 1)));
    reachablePositions.put(
        directions[2],
        reachablePositions
            .get(directions[0])
            .contains(mapUtil.translateInDirection(mapUtil.getMyPosition(), directions[2], 1))
            ? reachablePositions.get(directions[0])
            : reachablePositions
            .get(directions[1])
            .contains(mapUtil.translateInDirection(mapUtil.getMyPosition(), directions[2], 1))
            ? reachablePositions.get(directions[1])
            : mapUtil.reachablePositions(mapUtil.translateInDirection(mapUtil.getMyPosition(), directions[2], 1)));

    mapUtil.sortDirectionsBy(
        directions,
        Comparator.comparing(
            d ->
                mapUtil.translateInDirection(mapUtil.getMyPosition(), d, 1)
                    .getManhattanDistanceTo(center)));
    mapUtil.sortDirectionsBy(
        directions, Comparator.comparing(d -> mapUtil.openMovesInDirection(d) == 3 ? 0 : 1));
    mapUtil.sortDirectionsBy(
        directions, Comparator.comparing(d -> -reachablePositions.get(d).size()));
    mapUtil.sortDirectionsBy(
        directions, Comparator.comparing(mapUtil::snakeHeadsInDirection));
    mapUtil.sortDirectionsBy(
        directions, Comparator.comparing(d -> mapUtil.openMovesInDirection(d) == 0 ? 1 : 0));
    mapUtil.sortDirectionsBy(
        directions, Comparator.comparing(d -> mapUtil.canIMoveInDirection(d) ? 0 : 1));

    if (directions[0] == rightDirection) {
      turnLeft = !turnLeft;
    }
    currentDirection = directions[0];
    registerMove(mapUpdateEvent.getGameTick(), currentDirection);
    System.out.println(System.currentTimeMillis() - startTime);
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
