package com.shaneschulte.gpdynmap;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent;
import me.ryanhamshire.GriefPrevention.events.ClaimModifiedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class GPDynmap extends JavaPlugin implements Listener {
    private DynmapAPI m_dynmapAPI = null;
    private MarkerAPI m_dynmapMarkerAPI = null;
    private GriefPrevention m_griefPreventionPlugin = null;
    private MarkerSet m_griefPreventionMarkerSet = null;
    private Map<Long, AreaMarker> m_claims = new HashMap<>();

    public void onEnable()
    {
        super.onEnable();

        PluginManager pluginManager = getServer().getPluginManager();
        Plugin dynmapPlugin = pluginManager.getPlugin("dynmap");
        if (dynmapPlugin == null)
        {
            getLogger().log(Level.SEVERE, ("The dynmap plugin was not found on this server..."));
            pluginManager.disablePlugin(this);
            return;
        }
        this.m_dynmapAPI = ((DynmapAPI)dynmapPlugin);
        this.m_dynmapMarkerAPI = this.m_dynmapAPI.getMarkerAPI();

        Plugin griefPreventionPlugin = pluginManager.getPlugin("GriefPrevention");
        if (griefPreventionPlugin == null)
        {
            getLogger().log(Level.SEVERE, ("The grief prevention plugin was not found on this server..."));
            pluginManager.disablePlugin(this);
            return;
        }
        this.m_griefPreventionPlugin = ((GriefPrevention)griefPreventionPlugin);
        if ((!dynmapPlugin.isEnabled()) || (!griefPreventionPlugin.isEnabled()))
        {
            getLogger().log(Level.SEVERE, ("Either dynmap or grief prevention is disabled..."));
            pluginManager.disablePlugin(this);
            return;
        }
        if (!setupMarkerSet())
        {
            getLogger().log(Level.SEVERE, ("Failed to setup a marker set..."));
            pluginManager.disablePlugin(this);
            return;
        }

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, this::initClaims, 20L);

        getLogger().log(Level.INFO, "Succesfully enabled.");

        pluginManager.registerEvents(this, this);
    }

    public void onDisable()
    {
        for (AreaMarker marker : this.m_claims.values()) {
            marker.deleteMarker();
        }
        this.m_claims.clear();

        this.m_griefPreventionMarkerSet.deleteMarkerSet();
    }

    @EventHandler
    public void onResizeClaim(ClaimModifiedEvent event) {
        AreaMarker mark = createClaimMarker(event.getClaim());
        if(mark != null && !this.m_claims.containsKey(event.getClaim().getID())) {
            this.m_claims.put(event.getClaim().getID(), mark);
        }
    }

    @EventHandler
    public void onCreateClaim(ClaimCreatedEvent event) {
        AreaMarker mark = createClaimMarker(event.getClaim());
        if(mark != null && !this.m_claims.containsKey(event.getClaim().getID())) {
            this.m_claims.put(event.getClaim().getID(), mark);
        }
    }

    @EventHandler
    public void onDeleteClaim(ClaimDeletedEvent event) {
        if(this.m_claims.containsKey(event.getClaim().getID())) {
            AreaMarker mark = this.m_claims.remove(event.getClaim().getID());
            mark.deleteMarker();
        }
    }

    private boolean setupMarkerSet()
    {
        this.m_griefPreventionMarkerSet = this.m_dynmapMarkerAPI.getMarkerSet("griefprevention.markerset");

        String layerName = "Claims";
        if (this.m_griefPreventionMarkerSet == null) {
            this.m_griefPreventionMarkerSet = this.m_dynmapMarkerAPI.createMarkerSet("griefprevention.markerset", layerName, null, false);
        } else {
            this.m_griefPreventionMarkerSet.setMarkerSetLabel(layerName);
        }
        if (this.m_griefPreventionMarkerSet == null)
        {
            getLogger().log(Level.SEVERE, ("Failed to create a marker set with the name 'griefprevention.markerset'."));
            return false;
        }
        this.m_griefPreventionMarkerSet.setLayerPriority(10);
        this.m_griefPreventionMarkerSet.setHideByDefault(false);
        return true;
    }

    private void initClaims()
    {
        Collection<Claim> claims = this.m_griefPreventionPlugin.dataStore.getClaims();

        for (Claim claim : claims)
        {
            AreaMarker mark = createClaimMarker(claim);
            if(mark != null) {
                this.m_claims.put(claim.getID(), mark);
            }
        }
    }

    private AreaMarker createClaimMarker(Claim claim)
    {
        Location lowerBounds = claim.getLesserBoundaryCorner();
        Location higherBounds = claim.getGreaterBoundaryCorner();
        if ((lowerBounds == null) || (higherBounds == null)) {
            return null;
        }
        String worldname = lowerBounds.getWorld().getName();
        String owner = claim.getOwnerName();

        double[] x = new double[4];
        double[] z = new double[4];
        x[0] = lowerBounds.getX();
        z[0] = lowerBounds.getZ();
        x[1] = lowerBounds.getX();
        z[1] = (higherBounds.getZ() + 1.0D);
        x[2] = (higherBounds.getX() + 1.0D);
        z[2] = (higherBounds.getZ() + 1.0D);
        x[3] = (higherBounds.getX() + 1.0D);
        z[3] = lowerBounds.getZ();

        String markerid = "Claim_" + claim.getID();
        AreaMarker marker;
        if (!m_claims.containsKey(claim.getID()))
        {
            marker = this.m_griefPreventionMarkerSet.createAreaMarker(markerid, owner, false, worldname, x, z, false);
        }
        else
        {
            marker = m_claims.get(claim.getID());
            marker.setCornerLocations(x, z);
            marker.setLabel(owner);
        }
        setMarkerStyle(marker, claim.isAdminClaim());

        String desc = formatInfoWindow(claim);
        marker.setDescription(desc);

        return marker;
    }

    private void setMarkerStyle(AreaMarker marker, boolean isAdmin)
    {
        int lineColor, fillColor;

        // TODO: configuration
        if(isAdmin)
            lineColor = fillColor = 16711680;
        else
            lineColor = fillColor = 39880;

        int lineWeight = 2;
        double lineOpacity = 0.8D;
        double fillOpacity = 0.35D;

        marker.setLineStyle(lineWeight, lineOpacity, lineColor);
        marker.setFillStyle(fillOpacity, fillColor);
    }

    private String formatInfoWindow(Claim claim)
    {
        boolean isAdmin = claim.isAdminClaim();
        String owner = claim.getOwnerName();
        return "<div class=\"regioninfo\"><center><div class=\"infowindow\"><span style=\"font-weight:bold;\">" + owner + "'s claim</span><br/>" + (isAdmin ? "" : "<img src='https://minotar.net/helm/" + owner + "/20' />") + "</div>" + "</center>" + "</div>";
    }
}
