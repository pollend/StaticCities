/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.staticCities.bldg.gen;

import com.google.common.collect.Sets;
import java.util.Set;
import org.terasology.commonworld.Orientation;
import org.terasology.math.Side;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.ImmutableVector2i;
import org.terasology.math.geom.ImmutableVector3i;
import org.terasology.math.geom.LineSegment;
import org.terasology.math.geom.Rect2i;
import org.terasology.math.geom.Vector2i;
import org.terasology.staticCities.DefaultBlockType;
import org.terasology.staticCities.bldg.Building;
import org.terasology.staticCities.bldg.DefaultBuilding;
import org.terasology.staticCities.bldg.RectBuildingPart;
import org.terasology.staticCities.common.Edges;
import org.terasology.staticCities.deco.SingleBlockDecoration;
import org.terasology.staticCities.door.SimpleDoor;
import org.terasology.staticCities.model.roof.DomeRoof;
import org.terasology.staticCities.model.roof.HipRoof;
import org.terasology.staticCities.model.roof.Roof;
import org.terasology.staticCities.model.roof.SaddleRoof;
import org.terasology.staticCities.parcels.StaticParcel;
import org.terasology.staticCities.surface.InfiniteSurfaceHeightFacet;
import org.terasology.staticCities.window.SimpleWindow;
import org.terasology.utilities.random.MersenneRandom;
import org.terasology.utilities.random.Random;

/**
 *
 */
public class RectHouseGenerator {

    public Building apply(StaticParcel staticParcel, InfiniteSurfaceHeightFacet hm) {

        // use the rectangle, not the lot itself, because its hashcode is the identity hashcode
        Random rng = new MersenneRandom(staticParcel.getShape().hashCode());

        Orientation o = staticParcel.getOrientation();
        DefaultBuilding bldg = new DefaultBuilding(o);
        int inset = 2;
        Rect2i layout = staticParcel.getShape().expand(new Vector2i(-inset, -inset));

        Vector2i doorPos = Edges.getCorner(layout, o);

        // use door as base height for the entire building
        ImmutableVector2i doorDir = o.getDir();
        Vector2i probePos = new Vector2i(doorPos.getX() + doorDir.getX(), doorPos.getY() + doorDir.getY());

        // we add +1, because the building starts at 1 block above the terrain
        int floorHeight = TeraMath.floorToInt(hm.getWorld(probePos)) + 1;
        int wallHeight = 3;

        int roofBaseHeight = floorHeight + wallHeight;

        Roof roof = createRoof(rng, layout, roofBaseHeight);

        RectBuildingPart part = new RectBuildingPart(layout, roof, floorHeight, wallHeight);
        bldg.addPart(part);

        int doorHeight = 2;

        SimpleDoor door = new SimpleDoor(o, doorPos, floorHeight, floorHeight + doorHeight);
        part.addDoor(door);

        for (int i = 0; i < 3; i++) {
            // use the other three cardinal directions to place windows
            Orientation orient = o.getRotated(90 * (i + 1));
            Set<SimpleWindow> wnds = createWindows(layout, floorHeight, orient);

            for (SimpleWindow wnd : wnds) {
                // test if terrain outside window is lower than window base height
                ImmutableVector2i wndDir = wnd.getOrientation().getDir();
                ImmutableVector2i wndPos = wnd.getPos();
                Vector2i probePosWnd = new Vector2i(wndPos.getX() + wndDir.getX(), wndPos.getY() + wndDir.getY());
                if (wnd.getHeight() > hm.getWorld(probePosWnd)) {
                    part.addWindow(wnd);
                }
            }
        }

        addDecorations(part, o.getOpposite(), floorHeight, rng);

        return bldg;
    }

    private void addDecorations(RectBuildingPart part, Orientation o, int baseHeight, Random rng) {
        Rect2i rc = part.getShape().expand(-1, -1); // inside
        if (rng.nextBoolean()) {
            Vector2i pos = Edges.getCorner(rc, o.getRotated(-45));
            ImmutableVector3i pos3d = new ImmutableVector3i(pos.x(), baseHeight, pos.y());
            part.addDecoration(new SingleBlockDecoration(DefaultBlockType.BARREL, pos3d, Side.FRONT));
        }
        if (rng.nextBoolean()) {
            Vector2i pos = Edges.getCorner(rc, o.getRotated(45));
            ImmutableVector3i pos3d = new ImmutableVector3i(pos.x(), baseHeight, pos.y());
            part.addDecoration(new SingleBlockDecoration(DefaultBlockType.BARREL, pos3d, Side.FRONT));
        }
    }

    private Set<SimpleWindow> createWindows(Rect2i rc, int baseHeight, Orientation o) {

        final int wndBase = baseHeight + 1;
        final int endDist = 2;
        final int interDist = 2;
        final int wndSize = 1;

        Set<SimpleWindow> result = Sets.newHashSet();

        LineSegment borderSeg = Edges.getEdge(rc, o);
        Rect2i border = Rect2i.createEncompassing(new Vector2i(borderSeg.getStart()), new Vector2i(borderSeg.getEnd()));
        int step = interDist + wndSize;

        int firstX = border.minX() + endDist;
        int lastX = border.minX() + border.width() - endDist * 2;

        for (int x = firstX; x <= lastX; x += step) {
            Vector2i pos = new Vector2i(x, border.minY());
            SimpleWindow w = new SimpleWindow(o, pos, wndBase);
            result.add(w);
        }

        int firstY = border.minY() + endDist;
        int lastY = border.minY() + border.height() - endDist * 2;

        for (int y = firstY; y <= lastY; y += step) {
            Vector2i pos = new Vector2i(border.minX(), y);
            SimpleWindow w = new SimpleWindow(o, pos, wndBase);
            result.add(w);
        }

        return result;
    }

    private Roof createRoof(Random r, Rect2i layout, int roofBaseHeight) {
        // the roof area is 1 block larger all around
        Rect2i roofArea = layout.expand(new Vector2i(1, 1));

        int type = r.nextInt(100);

        if (type < 33) {
            int roofPitch = 1;
            return new HipRoof(layout, roofArea, roofBaseHeight, roofPitch, roofBaseHeight + 1);
        }

        if (type < 66) {
            return new DomeRoof(layout, roofArea, roofBaseHeight, Math.min(roofArea.width(), roofArea.height()) / 2);
        }

        boolean alongX = (roofArea.width() > roofArea.height());
        Orientation o = alongX ? Orientation.EAST : Orientation.NORTH;

        return new SaddleRoof(layout, roofArea, roofBaseHeight, o, 1);
    }

}
