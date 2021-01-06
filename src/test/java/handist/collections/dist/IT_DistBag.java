package handist.collections.dist;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import apgas.MultipleException;
import handist.mpijunit.MpiConfig;
import handist.mpijunit.MpiRunner;
import handist.mpijunit.launcher.TestLauncher;

/**
 * Test class for the distributed features of {@link DistBag}
 */
@RunWith(MpiRunner.class)
@MpiConfig(ranks=2, launcher=TestLauncher.class)
public class IT_DistBag implements Serializable {

	/** Number of elements to initialize on each host */
	static final int NB_ELEMS [] = {100, 50};

	static final int NB_LISTS [] = {4, 4};
	static Random random = new Random(12345l);

	/** Serial Version UID */
	private static final long serialVersionUID = 7668710704105520109L;

	/** World place group */
	static final TeamedPlaceGroup WORLD = TeamedPlaceGroup.getWorld();

	public static String genRandomString(String header) {
		long rand = random.nextLong();
		return header + rand;
	}

	/** Instance under test */
	DistBag<Element> distBag;

	@After
	public void cleanup() throws Throwable {
		distBag.destroy();
	}

	@Before
	public void setup() throws Throwable {
		distBag = new DistBag<>();
		WORLD.broadcastFlat(()-> {
			int here = WORLD.rank();
			for (int listNumber = 0; listNumber < NB_LISTS[here]; listNumber ++) {
				List<Element> l = new ArrayList<>(NB_ELEMS[here]);
				for (int i = 0; i < NB_ELEMS[here]; i++) {
					l.add(new Element(genRandomString(here + "p")));
				}
				distBag.addBag(l);
			}
		});
	}

	@Test
	public void testGlobalForEach() throws Throwable {
		// Add a prefix to all Element.s members
		distBag.GLOBAL.forEach((e)->{
			e.s = "GLOBAL" + e.s;
		});

		//Check that all elements on all places have the new prefix
		try {
			WORLD.broadcastFlat(()->{
				// "normal" for loop on the elements of the local handle
				for (Element e : distBag) {
					assertTrue(e.s.startsWith("GLOBAL"));
				}
			});
		} catch (MultipleException me) {
			me.printStackTrace();
			throw me.getSuppressed()[0];
		}
	}

	@Test
	public void testGlobalParallelForEach() throws Throwable {
		// Add a prefix to all Element.s members
		distBag.GLOBAL.parallelForEach((e)->{
			e.s = "GLOBAL" + e.s;
		});

		//Check that all elements on all places have the new prefix
		try {
			WORLD.broadcastFlat(()->{
				// "normal" for loop on the elements of the local handle
				for (Element e : distBag) {
					assertTrue(e.s.startsWith("GLOBAL"));
				}
			});
		} catch (MultipleException me) {
			me.printStackTrace();
			throw me.getSuppressed()[0];
		}
	}
	
	@Test(timeout=5000)
	public void testGlobalSize() throws Throwable {
		long [] size = new long [WORLD.size()];
		long [] expected = new long [WORLD.size()];
		for (int i = 0; i < WORLD.size(); i++) {
			expected[i] = NB_ELEMS[i] * NB_LISTS[i];
		}

		distBag.GLOBAL.size(size);


		assertArrayEquals(expected, size);
	}
	
	@Test
	public void testSetup() throws Throwable {
		WORLD.broadcastFlat(()-> {
			int here = WORLD.rank();
			assertEquals(NB_LISTS[here] * NB_ELEMS[here], distBag.size());
			for (Element e : distBag) {
				assertTrue(e.s.startsWith(here + "p"));
			}
		});
	}

	@Test(timeout=5000)
	public void testTeamSize() throws Throwable {
		long [] expected = new long [WORLD.size()];
		for (int i = 0; i < WORLD.size(); i++) {
			expected[i] = NB_ELEMS[i] * NB_LISTS[i];
		}

        try {
		WORLD.broadcastFlat(()-> {
			final long [] size = new long [WORLD.size()];
			distBag.TEAM.size(size);
			assertArrayEquals(expected, size);
		});
		} catch (MultipleException me) {
		    System.err.println("Error occurred in testTeamSize: Suppressed errors were:");
		    for (Throwable t : me.getSuppressed()) {
			t.printStackTrace();
		    }
		    throw me.getSuppressed()[0];
		}
	}
}