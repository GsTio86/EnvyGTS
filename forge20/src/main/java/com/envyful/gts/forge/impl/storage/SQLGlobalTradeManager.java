package com.envyful.gts.forge.impl.storage;

import com.envyful.api.database.impl.redis.Subscribe;
import com.envyful.api.forge.chat.UtilChatColour;
import com.envyful.api.forge.player.util.UtilPlayer;
import com.envyful.api.player.EnvyPlayer;
import com.envyful.gts.api.Trade;
import com.envyful.gts.api.sql.EnvyGTSQueries;
import com.envyful.gts.api.utils.TradeIDUtils;
import com.envyful.gts.forge.EnvyGTSForge;
import com.envyful.gts.forge.impl.ForgeGlobalTradeManager;
import com.envyful.gts.forge.impl.TradeFactory;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SQLGlobalTradeManager extends ForgeGlobalTradeManager {

    public SQLGlobalTradeManager() {
        EnvyGTSForge.getRedisDatabase().subscribe(this);
        try (Connection connection = EnvyGTSForge.getDatabase().getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(EnvyGTSQueries.GET_ALL_TRADES)) {
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                this.activeTrades.add(TradeFactory.fromResultSet(resultSet));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Subscribe("trade_update_channel")
    @Override
    public void syncTrade(String channel, String message) {
        String[] parts = message.split(":");
        if (parts.length < 3) {
            return;
        }
        String identifier = parts[0];

        if (identifier.equals(TradeIDUtils.SERVER_IDENTIFIER)) {
            return;
        }

        String tradeId = parts[1];
        String action = parts[2];

        synchronized (this.activeTrades) {
            switch (action) {
                case "PURCHASED":
                case "REMOVED":
                    this.activeTrades.removeIf(trade -> trade.getTradeId().equals(tradeId));
                    break;
                case "WAS_PURCHASED":
                    ServerPlayer target = UtilPlayer.getOnlinePlayer(UUID.fromString(parts[3]));
                    if (target == null) return;
                    target.sendSystemMessage(UtilChatColour.colour(
                        EnvyGTSForge.getLocale().getMessages().getItemWasPurchased()
                            .replace("%item%", parts[5])
                            .replace("%buyer%", parts[4])
                            .replace("%tax%", parts[6])
                            .replace("%price%", parts[7])
                    ));
                    break;
                case "NEW":
                    try (Connection connection = EnvyGTSForge.getDatabase().getConnection();
                         PreparedStatement preparedStatement = connection.prepareStatement(EnvyGTSQueries.GET_TRADE_BY_ID)) {
                        preparedStatement.setString(1, tradeId);
                        ResultSet resultSet = preparedStatement.executeQuery();

                        if (resultSet.next()) {
                            Trade newTrade = TradeFactory.fromResultSet(resultSet);
                            this.activeTrades.add(newTrade);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }

    @Override
    public boolean addTrade(EnvyPlayer<?> player, Trade trade) {
        if (!super.addTrade(player, trade)) {
            return false;
        }

        trade.save();

        String notificationMessage = String.format("%s:%s:%s", TradeIDUtils.SERVER_IDENTIFIER, trade.getTradeId(), "NEW");
        EnvyGTSForge.getRedisDatabase().publish("trade_update_channel", notificationMessage);
        return true;
    }

}
