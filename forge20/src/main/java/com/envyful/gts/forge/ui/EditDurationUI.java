package com.envyful.gts.forge.ui;

import com.envyful.api.concurrency.UtilConcurrency;
import com.envyful.api.forge.chat.UtilChatColour;
import com.envyful.api.forge.concurrency.UtilForgeConcurrency;
import com.envyful.api.forge.player.ForgeEnvyPlayer;
import com.envyful.api.time.UtilTime;
import com.envyful.api.time.UtilTimeFormat;
import com.envyful.gts.forge.EnvyGTSForge;
import com.envyful.gts.forge.impl.trade.ForgeTrade;
import com.envyful.gts.forge.impl.trade.type.PokemonTrade;
import com.envyful.gts.forge.player.GTSAttribute;
import com.pixelmonmod.pixelmon.api.dialogue.DialogueButton;
import com.pixelmonmod.pixelmon.api.dialogue.DialogueFactory;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PCBox;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;

import java.util.concurrent.TimeUnit;

public class EditDurationUI {

    public static void openUI(ForgeEnvyPlayer player, int page, int position, boolean error) {
        player.getParent().closeContainer();
        GTSAttribute attribute = player.getAttributeNow(GTSAttribute.class);
        Pokemon pokemon = SelectPriceUI.getPokemon(player, page, position);

        UtilForgeConcurrency.runLater(() -> DialogueFactory.builder()
                .title(UtilChatColour.colour(EnvyGTSForge.getLocale().getDurationInputDialogueTitle()))
                .description(UtilChatColour.colour((!error ?
                        EnvyGTSForge.getLocale().getDurationInputDialogueText() :
                        EnvyGTSForge.getLocale().getDurationInputDialogueErrorText())
                        .replace("%min_price%", String.format(EnvyGTSForge.getLocale().getMoneyFormat(), attribute.getCurrentPrice()))
                        .replace("%min_duration%", UtilTimeFormat.getFormattedDuration(
                                TimeUnit.SECONDS.toMillis(EnvyGTSForge.getConfig().getMinTradeDuration())
                        ))
                        .replace("%max_duration%", UtilTimeFormat.getFormattedDuration(
                                TimeUnit.SECONDS.toMillis(EnvyGTSForge.getConfig().getMinTradeDuration())
                        ))
                        .replace("%pokemon%", pokemon.getDisplayName().getString())))
                .defaultText(TimeUnit.SECONDS.toMinutes(EnvyGTSForge.getConfig().getMinTradeDuration()) + "m")
                .closeOnEscape()
                .onClose(closedScreen -> {
                    if (page == -1) {
                        SelectPartyPokemonUI.openUI(player);
                    } else {
                        SelectPCPokemonUI.openUI(player, page);
                    }
                })
                .buttons(DialogueButton.builder()
                        .text("Submit")
                        .onClick(submitted -> {
                            UtilConcurrency.runAsync(() -> {
                                long inputtedValue = UtilTime.attemptParseTime(submitted.getInput()).orElse(-1L);

                                if (inputtedValue < TimeUnit.SECONDS.toMillis(EnvyGTSForge.getConfig().getMinTradeDuration()) || inputtedValue < 0) {
                                    openUI(player, page, position, true);
                                    return;
                                }

                                if (inputtedValue > TimeUnit.SECONDS.toMillis(EnvyGTSForge.getConfig().getMaxTradeDurationSeconds())) {
                                    openUI(player, page, position, true);
                                    return;
                                }

                                Pokemon pixelmon;

                                if (page == -1) {
                                    PlayerPartyStorage party = StorageProxy.getPartyNow(player.getParent());
                                    pixelmon = party.get(position);
                                    party.set(position, null);
                                } else {
                                    PCBox box = StorageProxy.getPCForPlayerNow(player.getParent()).getBox(page);
                                    pixelmon = box.get(position);
                                    box.set(position, null);
                                }

                                EnvyGTSForge.getTradeManager()
                                        .addTrade(player, ((PokemonTrade.Builder) ForgeTrade.builder()
                                                .cost(attribute.getCurrentPrice())
                                                .expiry(System.currentTimeMillis() + inputtedValue)
                                                .owner(player)
                                                .originalOwnerName(player.getName())
                                                .content("p"))
                                                .contents(pixelmon)
                                                .build());
                                attribute.setCurrentDuration(0);
                                attribute.setCurrentMinPrice(0);
                                attribute.setCurrentPrice(0);
                                attribute.setSelectedSlot(-1);
                            });
                        })
                        .build())
                .sendTo(player.getParent()), 5);
    }

}
