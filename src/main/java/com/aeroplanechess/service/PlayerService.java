package com.aeroplanechess.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.aeroplanechess.builder.GameBuilder;
import com.aeroplanechess.model.Game;
import com.aeroplanechess.model.Player;
import com.aeroplanechess.repository.GameRepository;
import com.aeroplanechess.repository.PlayerRepository;
import com.aeroplanechess.utils.GameUtils;

public class PlayerService extends AbstractWebSocketService {

	Logger logger = LoggerFactory.getLogger(GameService.class);

	@Autowired
	PlayerRepository playerRepository;

	@Autowired
	GameRepository gameRepository;

	@Autowired
	GameService gameService;

	@Autowired
	GameBuilder gameBuilder;

	@Autowired
	GameUtils gameUtils;

	public String addPlayer(String sessionId, String name) {
		logger.info("addPlayer, sessionId: " + sessionId);
		Map<String, Game> waitingGameMap = gameRepository.getWaitingGameMap();
		Game game = null;
		game = waitingGameMap.values().stream().filter(g -> g.getJoinCount().getAndIncrement() <= 4).findAny().orElse(null);
		// create new game, if no available waiting game exists
		if (game == null)
			game = new Game();
		return addPlayer(sessionId, name, game);
	}

	public String addPlayer(String sessionId, String gameId, String name) {
		logger.info("addPlayer, sessionId: " + sessionId + ", gameId: " + gameId + ", name: " + name);
		Game game = gameRepository.getWaitingGame(gameId);
		if (game != null && game.getJoinCount().incrementAndGet() <= 4)
			return addPlayer(sessionId, name, game);
		else
			sendTo("joined", sessionId, "error", true);
		return null;
	}

	String addPlayer(String sessionId, String name, Game game) {
		logger.info("addPlayer, sessionId: " + sessionId + ", game: " + game + ", name: " + name);
		if (game == null)
			return null;
		String gameId = null;
		int i = 0;
		Player[] players = game.getPlayers();

		for (; i < players.length; i++) {
			if (players[i] == null) {
				players[i] = new Player(name, i, sessionId);
				break;
			}
		}
		gameId = game.getId();
		playerRepository.addPlayer(sessionId, gameId);
		sendTo("joined", sessionId, new String[] { "error", "game-id", "index" }, new Object[] { false, gameId, i });
		return gameId;
	}

	public void removePlayer(String sessionId) {
		logger.info("removePlayer, sessionId: " + sessionId);
		Map<String, String> playerGameMap = playerRepository.getPlayerGameMap();
		if (!playerGameMap.containsKey(sessionId))
			return;
		String gameId = playerGameMap.get(sessionId);
		Game game = gameRepository.getWaitingGame(gameId);
		boolean isWaiting = true;
		if (game == null) {
			game = gameRepository.getPlayingGame(gameId);
			isWaiting = false;
		}
		playerRepository.removePlayer(sessionId);
		Player[] players = game.getPlayers();
		int i;
		for (i = 0; i < players.length; i++) {
			if (players[i] != null && players[i].getSessionId().equals(sessionId)) {
				players[i] = null;
				break;
			}
		}
		if (isWaiting) {
			if (game.getReadyCount().get() > game.getJoinCount().decrementAndGet())
				game.getReadyCount().decrementAndGet();
		} else {
			if (game.getReadyCount().decrementAndGet() == 1) {
				gameRepository.removePlayingGame(gameId);
				send("won", gameId, "player-won", gameUtils.lastPlayerIndex(players));
				return;
			}
			gameUtils.allBackToBase(game.getAeroplanes(), i);
			send("move-result", game.getId(), new String[] { "aeroplanes", "leaved" }, new Object[] { game.getAeroplanes(), i, i });
			if (i == game.getCurrentPlayerIndex())
				gameService.nextTurn(game, false);
		}
		send("player-list", game.getId(), "players", players);
	}

	public void ready(String sessionId, String gameId) {
		logger.info("ready, sessionId: " + sessionId + " , gam");
		Game game = gameRepository.getWaitingGame(gameId);
		if (game == null)
			return;
		send("player-list", gameId, "players", game.getPlayers());
		if (game.getReadyCount().incrementAndGet() == 4) {
			gameRepository.addPlayingGame(gameId, gameRepository.removeWaitingGame(gameId));
			send("start", gameId, "start", true);
			gameService.nextTurn(game, false);
		}
	}

	Game newGame() {
		Game game = gameBuilder.build();
		gameRepository.getWaitingGameMap().put(game.getId(), game);
		return game;
	}

}
