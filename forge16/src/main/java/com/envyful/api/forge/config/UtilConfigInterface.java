package com.envyful.api.forge.config;

import com.envyful.api.config.type.ConfigInterface;
import com.envyful.api.config.type.ConfigItem;
import com.envyful.api.config.type.PaginatedConfigInterface;
import com.envyful.api.forge.chat.UtilChatColour;
import com.envyful.api.forge.gui.close.ForgeCloseConsumer;
import com.envyful.api.forge.player.ForgeEnvyPlayer;
import com.envyful.api.forge.player.ForgePlayerManager;
import com.envyful.api.gui.factory.GuiFactory;
import com.envyful.api.gui.item.Displayable;
import com.envyful.api.gui.pane.Pane;
import com.envyful.api.math.TriConsumer;
import com.envyful.api.text.Placeholder;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class UtilConfigInterface {

    public static void fillBackground(ForgeEnvyPlayer player,
                                      Pane pane, ConfigInterface settings,
                                      Placeholder... transformers) {
        for (var fillerItem : settings.getFillerItems()) {
            if (!fillerItem.isEnabled()) {
                continue;
            }

            pane.add(
                GuiFactory.displayable(
                    UtilConfigItem.fromConfigItem(
                        fillerItem, transformers
                    )));
        }

        for (var displayItem : settings.getDisplayItems()) {
            UtilConfigItem.builder().extendedConfigItem(player, pane, displayItem, transformers);
        }
    }

    public static void fillBackground(
        Pane pane, ConfigInterface settings,
        Placeholder... transformers) {
        for (ConfigItem fillerItem : settings.getFillerItems()) {
            if (!fillerItem.isEnabled()) {
                continue;
            }

            pane.add(
                GuiFactory.displayable(
                    UtilConfigItem.fromConfigItem(
                        fillerItem, transformers
                    )));
        }
    }

    public static void setBackground(
        Pane pane, ConfigInterface settings,
        Placeholder... transformers) {
        int position = 0;

        for (ConfigItem fillerItem : settings.getFillerItems()) {
            if (!fillerItem.isEnabled()) {
                ++position;
                continue;
            }

            pane.set(position % 9, position / 9,
                GuiFactory.displayable(
                    UtilConfigItem.fromConfigItem(
                        fillerItem, transformers
                    )));
            ++position;
        }
    }

    public static <T> PaginatedBuilder<T> paginatedBuilder(List<T> items) {
        return new PaginatedBuilder<T>().items(items);
    }

    public static class PaginatedBuilder<T> {

        private PaginatedConfigInterface configInterface;
        private Function<T, ConfigItem> itemConfigItemConversion;
        private Function<T, Displayable> itemDisplayableConversion;
        private ForgePlayerManager playerManager;
        private ForgeCloseConsumer closeConsumer =
            (ForgeCloseConsumer) GuiFactory.empty();
        private TriConsumer<ForgeEnvyPlayer, Displayable.ClickType, T>
            pageItemClickHandler = (forgeEnvyPlayer, clickType, t) -> {};

        private final List<T> items = Lists.newArrayList();
        private final List<BiConsumer<Pane, Integer>> extraItems =
            Lists.newArrayList();

        private PaginatedBuilder() {
            // Private constructor for static factory method
        }

        public PaginatedBuilder<T> configSettings(
            PaginatedConfigInterface configInterface) {
            this.configInterface = configInterface;
            return this;
        }

        public PaginatedBuilder<T> items(Collection<? extends T> items) {
            this.items.addAll(items);
            return this;
        }

        public PaginatedBuilder<T> items(T... items) {
            this.items.addAll(Arrays.asList(items));
            return this;
        }

        public PaginatedBuilder<T> itemDisplayableConversion(
            Function<T, ConfigItem> itemDisplayableConversion) {
            this.itemConfigItemConversion = itemDisplayableConversion;
            return this;
        }

        public PaginatedBuilder<T> itemConversion(
            Function<T, Displayable> itemDisplayableConversion) {
            this.itemDisplayableConversion = itemDisplayableConversion;
            return this;
        }

        public PaginatedBuilder<T> playerManager(
            ForgePlayerManager playerManager) {
            this.playerManager = playerManager;
            return this;
        }

        public PaginatedBuilder<T> closeConsumer(
            ForgeCloseConsumer closeConsumer) {
            this.closeConsumer = closeConsumer;
            return this;
        }

        public PaginatedBuilder<T> pageItemClickHandler(
            TriConsumer<ForgeEnvyPlayer, Displayable.ClickType, T>
                pageItemClickHandler) {
            this.pageItemClickHandler = pageItemClickHandler;
            return this;
        }

        public PaginatedBuilder<T> extraItems(
            Collection<BiConsumer<Pane, Integer>> extraItems) {
            this.extraItems.addAll(extraItems);
            return this;
        }

        public PaginatedBuilder<T> extraItems(
            BiConsumer<Pane, Integer>... extraItems) {
            this.extraItems.addAll(Arrays.asList(extraItems));
            return this;
        }

        public void open(ForgeEnvyPlayer player, Placeholder... placeholders) {
            open(player, 1, placeholders);
        }

        public void open(
            ForgeEnvyPlayer player, int page,
            Placeholder... placeholders) {
            Pane pane = GuiFactory.paneBuilder()
                .topLeftX(0)
                .topLeftY(0)
                .width(9)
                .height(this.configInterface.getHeight())
                .build();

            UtilConfigInterface.fillBackground(
                pane, this.configInterface, placeholders);

            int pages = (int) Math.ceil((double) this.items.size() / this.configInterface.getPositions().size());

            if (this.shouldShowChangePageButtons(page, pages)) {
                UtilConfigItem.builder()
                    .clickHandler((envyPlayer, clickType) -> {
                        if (this.configInterface.isLoopPages()) {
                            int nextPage = (page >= pages || pages == 0) ? 1 : page + 1;
                            open(player, nextPage, placeholders);
                        }
                    }).extendedConfigItem(player, pane, this.configInterface.getNextPageButton(), placeholders);

                UtilConfigItem.builder()
                    .clickHandler((envyPlayer, clickType) -> {
                        if (this.configInterface.isLoopPages()) {
                            int prevPage = (page <= 1 || pages == 0) ? pages : page - 1;
                            open(player, prevPage, placeholders);
                        }
                    }).extendedConfigItem(player, pane, this.configInterface.getPreviousPageButton(), placeholders);
            }

            int positions = this.configInterface.getPositions().size();

            for (int i = 0; i < positions; i++) {
                int itemId = ((page - 1) * this.configInterface.getPositions().size()) + i;

                if (itemId >= (this.items.size())) {
                    continue;
                }

                int position = this.configInterface.getPositions().get(i);
                int posX = position % 9;
                int posY = position / 9;
                T item = this.items.get(itemId);

                pane.set(posX, posY,
                    this.getDisplayable(player, item, placeholders));
            }

            for (BiConsumer<Pane, Integer> extraItem : this.extraItems) {
                extraItem.accept(pane, page);
            }

            GuiFactory.guiBuilder()
                .setPlayerManager(this.playerManager)
                .addPane(pane)
                .height(this.configInterface.getHeight())
                .closeConsumer(this.closeConsumer)
                .title(UtilChatColour.colour(
                    this.configInterface.getTitle()))
                .build().open(player);
        }

        private boolean shouldShowChangePageButtons(int page, int pages) {
            if (this.configInterface.isDisplayPageButtonsAtLimits()) {
                return true;
            }

            return page == pages || page == 1;
        }

        private Displayable getDisplayable(
            ForgeEnvyPlayer player, T
            item, Placeholder... placeholders) {

            if (this.itemDisplayableConversion != null) {
                return this.itemDisplayableConversion.apply(item);
            }

            return GuiFactory.displayableBuilder(
                    UtilConfigItem.fromConfigItem(
                        this.itemConfigItemConversion.apply(item),
                        placeholders))
                .singleClick()
                .asyncClick()
                .clickHandler((envyPlayer, clickType) ->
                    this.pageItemClickHandler.execute(
                        player, clickType, item))
                .build();
        }
    }
}