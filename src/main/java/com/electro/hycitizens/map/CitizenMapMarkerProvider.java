package com.electro.hycitizens.map;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.worldmap.ContextMenuItem;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarker;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarkerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.server.core.util.PositionUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class CitizenMapMarkerProvider implements WorldMapManager.MarkerProvider {
    private static final String MARKER_PREFIX = "HyCitizensMarker-";
    private final HyCitizensPlugin plugin;

    public CitizenMapMarkerProvider(@Nonnull HyCitizensPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void update(World world, Player viewer, MarkersCollector collector) {
        if (world == null || viewer == null || collector == null || plugin.getCitizensManager() == null) {
            return;
        }

        UUID worldUuid = world.getWorldConfig() != null ? world.getWorldConfig().getUuid() : null;
        if (worldUuid == null) {
            return;
        }

        List<String> markerImagesToDeliver = new ArrayList<>();

        for (CitizenData citizen : plugin.getCitizensManager().getAllCitizens()) {
            if (!citizen.isMapMarkerEnabled() || !worldUuid.equals(citizen.getWorldUUID())) {
                continue;
            }

            Vector3d position = citizen.getCurrentPosition() != null
                    ? citizen.getCurrentPosition()
                    : citizen.getPosition();
            if (position == null) {
                continue;
            }

            String markerImage = CitizenMapMarkerAsset.resolveMarkerImage(citizen);
            markerImagesToDeliver.add(markerImage);
            collector.add(createMarker(citizen, position, markerImage));
        }

        PlayerRef viewerRef = findViewerRef(world.getPlayerRefs(), viewer.getUuid());
        if (viewerRef != null && !markerImagesToDeliver.isEmpty()) {
            CitizenMapMarkerAsset.deliverAssetsToViewer(viewerRef, markerImagesToDeliver);
        }
    }

    @Nonnull
    private static MapMarker createMarker(@Nonnull CitizenData citizen, @Nonnull Vector3d position,
                                          @Nonnull String markerImage) {
        Transform transform = new Transform(new Vector3d(position), Vector3f.ZERO);
        String labelText = markerLabelText(citizen);
        return new MapMarker(
                MARKER_PREFIX + citizen.getId() + "-" + markerIdSegment(markerImage) + "-" + markerIdSegment(labelText),
                createLabel(labelText),
                markerImage,
                PositionUtil.toTransformPacket(transform),
                (ContextMenuItem[]) null,
                (MapMarkerComponent[]) null
        );
    }

    @Nonnull
    private static String markerIdSegment(@Nonnull String value) {
        return Integer.toHexString(value.hashCode()) + "-" + value.replaceAll("[^A-Za-z0-9]+", "-");
    }

    @Nullable
    private static FormattedMessage createLabel(@Nonnull String labelText) {
        if (labelText.isEmpty()) {
            return null;
        }

        FormattedMessage label = new FormattedMessage();
        label.rawText = labelText;
        return label;
    }

    @Nonnull
    private static String markerLabelText(@Nonnull CitizenData citizen) {
        String customName = citizen.getMapMarkerName();
        String name = (customName.isBlank() ? citizen.getName() : customName)
                .replace("\\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        return name;
    }

    @Nullable
    private static PlayerRef findViewerRef(@Nullable Collection<PlayerRef> playerRefs, @Nullable UUID viewerUuid) {
        if (playerRefs == null || viewerUuid == null) {
            return null;
        }

        for (PlayerRef playerRef : playerRefs) {
            if (playerRef != null && viewerUuid.equals(playerRef.getUuid())) {
                return playerRef;
            }
        }
        return null;
    }
}
