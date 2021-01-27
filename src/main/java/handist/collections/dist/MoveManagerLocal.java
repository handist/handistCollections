/*******************************************************************************
 * Copyright (c) 2021 Handy Tools for Distributed Computing (HanDist) project.
 *
 * This program and the accompanying materials are made available to you under
 * the terms of the Eclipse Public License 1.0 which accompanies this
 * distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 ******************************************************************************/
package handist.collections.dist;

import static apgas.Constructs.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import apgas.Place;
import handist.collections.dist.util.ObjectInput;
import handist.collections.dist.util.ObjectOutput;
import handist.collections.function.DeSerializer;
import handist.collections.function.Serializer;

/**
 * This class is used for relocating elements of DistCollections.
 */
public final class MoveManagerLocal {
    private static final boolean DEBUG = false;
    final Map<Place, List<DeSerializer>> builders;
    // TODO TeamedPlaceGroup or PlaceGroup<PlaceInTeam>
    final TeamedPlaceGroup placeGroup;
    final Map<Place, List<Serializer>> serializeListMap;

    /**
     * Construct a MoveManagerLocal with the given arguments.
     *
     * @param placeGroup the group hosts that will transfer objects between
     *                   themselves using this instance.
     */

    public MoveManagerLocal(TeamedPlaceGroup placeGroup) {
        this.placeGroup = placeGroup;
        serializeListMap = new HashMap<>(placeGroup.size());
        builders = new HashMap<>(placeGroup.size());
        for (final Place place : placeGroup.places()) {
            serializeListMap.put(place, new ArrayList<>());
            builders.put(place, new ArrayList<>());
        }
    }

    public void clear() {
        for (final List<Serializer> list : serializeListMap.values()) {
            list.clear();
        }
        for (final List<DeSerializer> list : builders.values()) {
            list.clear();
        }
    }

    @SuppressWarnings("unchecked")
    public void executeDeserialization(byte[] buf, int[] rcvOffset, int[] rcvSize) throws Exception {
        int current = 0;
        for (final Place p : placeGroup.places()) {
            final int size = rcvSize[current];
            final int offset = rcvOffset[current];
            current++;
            if (p.equals(here())) {
                continue;
            }

            final ByteArrayInputStream in = new ByteArrayInputStream(buf, offset, size);
            final ObjectInput ds = new ObjectInput(in);
            final List<DeSerializer> deserializerList = (List<DeSerializer>) ds.readObject();
            for (final DeSerializer deserialize : deserializerList) {
                deserialize.accept(ds);
            }
            ds.close();
        }
    }

    @SuppressWarnings("unchecked")
    public void executeDeserialization(Map<Place, byte[]> map) throws Exception {
        for (final Place p : placeGroup.places()) {
            if (p.equals(here())) {
                continue;
            }
            final byte[] buf = map.get(p);
            final ObjectInput ds = new ObjectInput(new ByteArrayInputStream(buf));
            final List<Consumer<ObjectInput>> deserializerList = (List<Consumer<ObjectInput>>) ds.readObject();
            for (final Consumer<ObjectInput> deserialize : deserializerList) {
                deserialize.accept(ds);
            }
            ds.close();
        }
    }

    public byte[] executeSerialization(Place place) throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ObjectOutput s = new ObjectOutput(out);
        s.writeObject(builders.get(place));
        for (final Serializer serializer : serializeListMap.get(place)) {
            serializer.accept(s);
        }
        s.close();
        return out.toByteArray();
    }

    public void executeSerialization(TeamedPlaceGroup placeGroup2, ByteArrayOutputStream out, int[] offsets,
            int[] sizes) throws IOException {
        for (int i = 0; i < placeGroup2.size(); i++) {
            final Place place = placeGroup2.get(i);
            // TODO is this correct??
            if (place.equals(here())) {
                continue;
            }
            offsets[i] = out.size();
            // TODO should reopen ByteArray...
            if (DEBUG) {
                System.out.println("execSeri: " + here() + "->" + place + ":start:" + out.size());
            }
            final ObjectOutput s = new ObjectOutput(out);
            s.writeObject(builders.get(place));
            for (final Serializer serializer : serializeListMap.get(place)) {
                serializer.accept(s);
            }
            s.close();
            if (DEBUG) {
                System.out.println("execSeri: " + here() + "->" + place + ":finish:" + out.size());
            }
            sizes[i] = out.size() - offsets[i];
        }
    }

    public void request(Place pl, Serializer serializer, DeSerializer deserializer) {
        serializeListMap.get(pl).add(serializer);
        builders.get(pl).add(deserializer);
    }

    /**
     * Request to reset the Serializer at the specified place.
     *
     * @param pl the target place.
     */
    public void reset(Place pl) {
        serializeListMap.get(pl).add((ObjectOutput s) -> {
            s.reset();
        });
    }

    /**
     * Execute the all requests synchronously.
     *
     * @throws Exception if a runtime exception is thrown at any stage during the
     *                   relocation
     */
    public void sync() throws Exception {
        CollectiveRelocator.all2allser(placeGroup, this);
    }

    /*
     * 将来的に moveAtSync(dist:RangedDistribution, mm) を 持つものを interface 宣言するのかな？
     * public def moveAssociativeCollectionsAtSync(dist: RangedDistribution, dists:
     * List[RangedMoballe]) {
     *
     * } public def moveAssosicativeCollectionsAtSync(dist: Distribution[K]) { //
     * add dist to the list to schedule }
     */
}
