package se.cygni.snake;

import static se.cygni.snake.api.model.SnakeDirection.DOWN;
import static se.cygni.snake.api.model.SnakeDirection.LEFT;
import static se.cygni.snake.api.model.SnakeDirection.RIGHT;
import static se.cygni.snake.api.model.SnakeDirection.UP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import se.cygni.snake.api.model.Map;
import se.cygni.snake.api.model.SnakeDirection;
import se.cygni.snake.client.MapCoordinate;
import se.cygni.snake.client.MapUtil;

public class ExtendedMapUtil extends MapUtil {
  private Map map;
  private String playerId;

  public ExtendedMapUtil(Map map, String playerId) {
    super(map, playerId);
    this.map = map;
    this.playerId = playerId;
  }

  public void sortDirectionsBy(SnakeDirection[] directions, Comparator<SnakeDirection> by) {
    if (by.compare(directions[1], directions[2]) > 0) {
      SnakeDirection temp = directions[1];
      directions[1] = directions[2];
      directions[2] = temp;
    }
    if (by.compare(directions[0], directions[1]) > 0) {
      SnakeDirection temp = directions[0];
      directions[0] = directions[1];
      directions[1] = temp;
    }
    if (by.compare(directions[1], directions[2]) > 0) {
      SnakeDirection temp = directions[1];
      directions[1] = directions[2];
      directions[2] = temp;
    }
  }

  public MapCoordinate translateInDirection(
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

  public SnakeDirection nextDirection(SnakeDirection direction) {
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

  public SnakeDirection lastDirection(SnakeDirection direction) {
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

  public int openMovesInDirection(SnakeDirection direction) {
    MapCoordinate nextPosition = translateInDirection(getMyPosition(), direction, 1);
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
                  return isTileAvailableForMovementTo(position);
                })
            .count();
  }

  public int snakeHeadsInDirection(SnakeDirection direction) {
    MapCoordinate nextPosition = translateInDirection(getMyPosition(), direction, 1);
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
                  return snakeHeads().contains(position);
                })
            .count();
  }

  public List<MapCoordinate> snakeHeads() {
    return Arrays.stream(map.getSnakeInfos())
        .filter(snakeInfo -> snakeInfo.isAlive() && !snakeInfo.getId().equals((playerId)))
        .map(snakeInfo -> translatePosition(snakeInfo.getPositions()[0]))
        .collect(Collectors.toList());
  }

  /*private List<MapCoordinate> snakeLasts(Map map, MapUtil mapUtil) {
    return Arrays.stream(map.getSnakeInfos())
        .filter(snakeInfo -> snakeInfo.isAlive() && !snakeInfo.getId().equals(getPlayerId()))
        .map(
            snakeInfo ->
                mapUtil.translatePosition(snakeInfo.getPositions()[snakeInfo.getLength() - 1]))
        .collect(Collectors.toList());
  }*/

  public List<MapCoordinate> reachablePositions(MapCoordinate position) {
    ArrayList<MapCoordinate> positions = new ArrayList<>();
    positions.add(position);
    for (int i = 0; i < positions.size(); i++) {
      Stream.of(
          positions.get(i).translateBy(-1, 0),
          positions.get(i).translateBy(1, 0),
          positions.get(i).translateBy(0, -1),
          positions.get(i).translateBy(0, 1))
          .filter(p -> !positions.contains(p) && isTileAvailableForMovementTo(p))
          .forEach(positions::add);
    }
    return positions;
  }
}
