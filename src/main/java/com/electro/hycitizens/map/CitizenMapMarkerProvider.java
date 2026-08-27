package com.electro.hycitizens.map;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.util.RotationUtil;
import com.hypixel.hytale.math.vector.Transform;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
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

        List<PendingMarker> pendingMarkers = new ArrayList<>();
        PlayerRef viewerRef = viewer.getPlayerRef();
        Vector3d viewerPosition = viewerRef != null && viewerRef.getTransform() != null
                ? new Vector3d(viewerRef.getTransform().getPosition())
                : null;

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
            if (!isWithinCustomMarkerDistance(citizen, position, viewerPosition)) {
                continue;
            }

            Rotation3f rotation = citizen.getCurrentRotation() != null
                    ? citizen.getCurrentRotation()
                    : (citizen.getRotation() != null ? RotationUtil.toRotation(citizen.getRotation()) : new Rotation3f());

            String markerImage = CitizenMapMarkerAsset.resolveMarkerImage(citizen);
            pendingMarkers.add(new PendingMarker(citizen, position, rotation, markerImage));
        }

        for (PendingMarker pendingMarker : pendingMarkers) {
            collector.addIgnoreViewDistance(createMarker(
                    pendingMarker.citizen(),
                    pendingMarker.position(),
                    pendingMarker.rotation(),
                    pendingMarker.markerImage()
            ));
        }
    }

    private record PendingMarker(CitizenData citizen, Vector3d position, Rotation3f rotation, String markerImage) {
    }

    @Nonnull
    private static MapMarker createMarker(@Nonnull CitizenData citizen, @Nonnull Vector3d position,
                                          @Nonnull Rotation3f rotation, @Nonnull String markerImage) {
        Transform transform = new Transform(new Vector3d(position), rotation);
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

    private static boolean isWithinCustomMarkerDistance(@Nonnull CitizenData citizen, @Nonnull Vector3d markerPosition, @Nullable Vector3d viewerPosition) {
        float maxDistance = citizen.getMapMarkerMaxDistance();
        if (maxDistance <= 0.0f || viewerPosition == null) {
            return true;
        }

        double dx = markerPosition.x - viewerPosition.x;
        double dz = markerPosition.z - viewerPosition.z;
        return dx * dx + dz * dz <= (double) maxDistance * maxDistance;
    }
}
