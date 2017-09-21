package com.aeroplanechess.service;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.aeroplanechess.model.Aeroplane;
import com.aeroplanechess.model.Game;
import com.aeroplanechess.model.Player;
import com.aeroplanechess.utils.DiceUtils;
import com.aeroplanechess.utils.GameBuilder;
import com.aeroplanechess.utils.MoveUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class GameService {

	// TODO review here!
	public static Game game;

	private Logger logger = LoggerFactory.getLogger(GameService.class);

	@Autowired
	SimpMessagingTemplate simpMessagingTemplate;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	GameBuilder gameBuilder;

	@Autowired
	DiceUtils diceUtils;

	@Autowired
	MoveUtils moveUtils;

	public void roll(String sessionId) {
		game.setLastRoll(diceUtils.roll());
		ObjectNode objectNode = objectMapper.createObjectNode();
		objectNode.put("roll", game.getLastRoll());
		objectNode.put("current", game.getCurrentPlayer());
		simpMessagingTemplate.convertAndSend("/game/roll-result", objectNode);
		objectNode = objectMapper.createObjectNode();
		objectNode.put("move", "true");
		simpMessagingTemplate.convertAndSend("/game/move-" + sessionId, objectNode);
	}

	public void newGame() {
		game = gameBuilder.build();
		// TODO return game id?
	}

	public void addPlayer(String sessionId) {
		if (game == null)
			newGame();
		Player player;
		Player[] players = game.getPlayers();
		for (int i = 0; i < players.length; i++) {
			if (players[i] == null) {
				player = new Player();
				player.setColor(i);
				player.setName("Player " + (i + 1));
				player.setSessionId(sessionId);
				players[i] = player;
				if (i == players.length - 1)
					game.setReady(true);
				break;
			}
		}
		game.setPlayers(players);
		simpMessagingTemplate.convertAndSend("/game/player-list", game.getPlayers());
	}

	public void removePlayer(String sessionId) {
		Player[] players = game.getPlayers();
		for (int i = 0; i < players.length; i++) {
			if (players[i] == null)
				continue;
			if (players[i].getSessionId().equals(sessionId)) {
				players[i] = null;
				break;
			}
		}
		game.setPlayers(players);
		simpMessagingTemplate.convertAndSend("/game/player-list", game.getPlayers());
	}

	public void checkStart() {
		logger.info("check start");
		if (!game.getReady())
			return;
		ObjectNode objectNode = objectMapper.createObjectNode();
		objectNode.put("start", true);
		simpMessagingTemplate.convertAndSend("/game/start", objectNode);
		nextTurn();
	}

	void nextTurn() {
		ObjectNode objectNode = objectMapper.createObjectNode();
		objectNode.put("your-turn", true);
		simpMessagingTemplate.convertAndSend("/game/your-turn-" + getPlayerSessionId(game.getCurrentPlayer() + 1),
				objectNode);
	}

	String getPlayerSessionId(int i) {
		Player[] players = game.getPlayers();
		if (i == players.length)
			i = 0;
		game.setCurrentPlayer(i);
		return players[i].getSessionId();
	}

	public void move(String sessionId, int aeroplaneIndex) {
		logger.info(game.getCurrentPlayer() + "moved");
		int startIndex = game.getCurrentPlayer() * 4;
		Aeroplane[] aeroplanes = moveUtils.move(game.getAeroplanes(), aeroplaneIndex + startIndex,
				game.getLastRoll());
		logger.info(Arrays.toString(game.getAeroplanes()));
		simpMessagingTemplate.convertAndSend("/game/move-result", aeroplanes);
		if (isWin()) {
			ObjectNode objectNode = objectMapper.createObjectNode();
			objectNode.put("playerWon", game.getCurrentPlayer());
			simpMessagingTemplate.convertAndSend("/game/won", objectNode);
		} else {
			// TODO review game rules here?
			nextTurn();
		}
	}

	boolean isWin() {
		Aeroplane[] aeroplanes = game.getAeroplanes();
		int count = 0;
		for (int i = game.getCurrentPlayer() * 4; i < i + 4; i++) {
			if (aeroplanes[i].getInCellId().substring(0, 2).equals("go"))
				count++;
			else
				break;
		}
		return count == 4;
	}

}
