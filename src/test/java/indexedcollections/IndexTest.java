package indexedcollections;

import static me.prettyprint.hector.api.beans.DynamicComposite.DEFAULT_DYNAMIC_COMPOSITE_ALIASES;
import static me.prettyprint.hector.api.ddl.ComparatorType.DYNAMICCOMPOSITETYPE;
import static me.prettyprint.hector.api.factory.HFactory.createColumn;
import static me.prettyprint.hector.api.factory.HFactory.createKeyspace;
import static me.prettyprint.hector.api.factory.HFactory.createMutator;
import static me.prettyprint.hector.api.factory.HFactory.getOrCreateCluster;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import indexedcollections.IndexedCollections.ContainerCollection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import me.prettyprint.cassandra.serializers.ByteBufferSerializer;
import me.prettyprint.cassandra.serializers.BytesArraySerializer;
import me.prettyprint.cassandra.serializers.DynamicCompositeSerializer;
import me.prettyprint.cassandra.serializers.LongSerializer;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.cassandra.serializers.UUIDSerializer;
import me.prettyprint.cassandra.service.ThriftCfDef;
import me.prettyprint.cassandra.service.ThriftKsDef;
import me.prettyprint.cassandra.testutils.EmbeddedServerHelper;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.Serializer;

import org.apache.cassandra.config.ConfigurationException;
import org.apache.cassandra.db.marshal.BytesType;
import org.apache.cassandra.db.marshal.TimeUUIDType;
import org.apache.cassandra.thrift.CfDef;
import org.apache.cassandra.thrift.KsDef;
import org.apache.log4j.Logger;
import org.apache.thrift.transport.TTransportException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Example class showing usage of IndexedCollections.
 */
public class IndexTest {

	private static final Logger logger = Logger.getLogger(IndexTest.class
			.getName());

	public static final String KEYSPACE = "Keyspace";

	public static final StringSerializer se = new StringSerializer();
	public static final ByteBufferSerializer be = new ByteBufferSerializer();
	public static final DynamicCompositeSerializer ce = new DynamicCompositeSerializer();
	public static final UUIDSerializer ue = new UUIDSerializer();
	public static final LongSerializer le = new LongSerializer();
	public static final BytesArraySerializer bae = new BytesArraySerializer();

	static EmbeddedServerHelper embedded;

	static Cluster cluster;
	static Keyspace ko;

	@Test
	public void testIndexes() throws IOException, TTransportException,
			InterruptedException, ConfigurationException {

		// Create a container entity

		UUID g1 = createEntity("company");

		ContainerCollection<UUID> container = new ContainerCollection<UUID>(g1,
				"employees");
		Set<ContainerCollection<UUID>> containers = new LinkedHashSet<ContainerCollection<UUID>>();
		containers.add(container);

		// Create a set of items to add to the container

		UUID e1 = createEntity("employee");
		UUID e2 = createEntity("employee");
		UUID e3 = createEntity("employee");

		// Create container/item relationship

		addEntityToCollection(container, e1);
		addEntityToCollection(container, e2);
		addEntityToCollection(container, e3);

		// Check the entities in the container

		List<UUID> entities = getEntitiesInCollection(container);
		assertEquals(3, entities.size());

		// Set name column values

		setEntityColumn(e1, "name", "bob", containers, se);

		setEntityColumn(e2, "name", "fred", containers, se);

		setEntityColumn(e3, "name", "bill", containers, se);

		// Do an exact match search for name column

		logger.info("SELECT WHERE name = 'fred'");

		List<UUID> results = searchContainer(container, "name", "fred");

		logger.info(results.size() + " results found");

		assertEquals(1, results.size());
		assertTrue(results.get(0).equals(e2));

		logger.info("Result found is " + results.get(0));

		// Change the value of a name column and make sure the old value is no
		// longer in the index

		setEntityColumn(e2, "name", "steve", containers, se);

		logger.info("SELECT WHERE name = 'fred'");

		results = searchContainer(container, "name", "fred");

		logger.info(results.size() + " results found");

		assertEquals(0, results.size());

		// Do a range search

		logger.info("SELECT WHERE name >= 'bill' AND name < 'c'");

		results = searchContainer(container, "name", "bill", "c", false);

		logger.info(results.size() + " results found");

		assertEquals(2, results.size());

		// Set column values for height

		setEntityColumn(e1, "height", (long) 5, containers, le);

		setEntityColumn(e2, "height", (long) 6, containers, le);

		setEntityColumn(e3, "height", (long) 7, containers, le);

		// Do an numeric exact match search for height

		logger.info("SELECT WHERE height = 6");

		results = searchContainer(container, "height", 6);

		logger.info(results.size() + " results found");

		assertEquals(1, results.size());

		// Do a numeric range search for height

		logger.info("SELECT WHERE height >= 6 AND name < 10");

		results = searchContainer(container, "height", 6, 10, false);

		logger.info(results.size() + " results found");

		assertEquals(2, results.size());

		// Change a numeric column value and make sure it's no longer in the
		// index

		setEntityColumn(e3, "height", (long) 5, containers, le);

		results = searchContainer(container, "height", 6, 10, false);

		logger.info(results.size() + " results found");

		assertEquals(1, results.size());

		// Set byte values in columns

		setEntityColumn(e1, "bytes", new byte[] { 1, 2, 3 }, containers, bae);

		setEntityColumn(e2, "bytes", new byte[] { 1, 2, 4 }, containers, bae);

		setEntityColumn(e3, "bytes", new byte[] { 1, 2, 5 }, containers, bae);

		// Do a byte array exact match search

		results = searchContainer(container, "bytes", new byte[] { 1, 2, 4 });

		logger.info(results.size() + " results found");

		assertEquals(1, results.size());

		// Do a byte array range search

		results = searchContainer(container, "bytes", new byte[] { 1, 2, 4 },
				new byte[] { 10 }, false);

		logger.info(results.size() + " results found");

		assertEquals(2, results.size());

		// Store some text columns

		setEntityColumn(e1, "location", "san francisco", containers, se);

		setEntityColumn(e2, "location", "san diego", containers, se);

		setEntityColumn(e3, "location", "santa clara", containers, se);

		// Do a range search exclusive on the same value for start and end and
		// make sure we get 0 results

		results = searchContainer(container, "location", "san francisco",
				"san francisco", false);

		logger.info(results.size() + " results found");

		assertEquals(0, results.size());

		// Do a range search inclusive on the same value for start and end and
		// make sure we get 1 result

		results = searchContainer(container, "location", "san francisco",
				"san francisco", true);

		logger.info(results.size() + " results found");

		assertEquals(1, results.size());

	}

	@BeforeClass
	public static void setup() throws TTransportException, IOException,
			InterruptedException, ConfigurationException {
		embedded = new EmbeddedServerHelper();
		embedded.setup();

		cluster = getOrCreateCluster("MyCluster", "127.0.0.1:9170");
		ko = createKeyspace(KEYSPACE, cluster);

		ArrayList<CfDef> cfDefList = new ArrayList<CfDef>(2);

		setupColumnFamilies(cfDefList);

		makeKeyspace(cluster, KEYSPACE,
				"org.apache.cassandra.locator.SimpleStrategy", 1, cfDefList);

	}

	@AfterClass
	public static void teardown() throws IOException {
		EmbeddedServerHelper.teardown();
		embedded = null;
	}

	/**
	 * Create the four required column families for values and indexes.
	 * 
	 * @param cfDefList
	 */
	public static void setupColumnFamilies(List<CfDef> cfDefList) {

		createCF(IndexedCollections.DEFAULT_ITEM_CF,
				BytesType.class.getSimpleName(), cfDefList);

		createCF(IndexedCollections.DEFAULT_COLLECTION_CF,
				TimeUUIDType.class.getSimpleName(), cfDefList);

		createCF(IndexedCollections.DEFAULT_COLLECTION_INDEX_CF,
				DYNAMICCOMPOSITETYPE.getTypeName()
						+ DEFAULT_DYNAMIC_COMPOSITE_ALIASES, cfDefList);

		createCF(IndexedCollections.DEFAULT_ITEM_INDEX_ENTRIES,
				DYNAMICCOMPOSITETYPE.getTypeName()
						+ DEFAULT_DYNAMIC_COMPOSITE_ALIASES, cfDefList);

	}

	public static void createCF(String name, String comparator_type,
			List<CfDef> cfDefList) {
		cfDefList.add(new CfDef(KEYSPACE, name)
				.setComparator_type(comparator_type).setKey_cache_size(0)
				.setRow_cache_size(0).setGc_grace_seconds(86400));
	}

	public static void makeKeyspace(Cluster cluster, String name,
			String strategy, int replicationFactor, List<CfDef> cfDefList) {

		if (cfDefList == null) {
			cfDefList = new ArrayList<CfDef>();
		}

		try {
			KsDef ksDef = new KsDef(name, strategy, cfDefList);
			cluster.addKeyspace(new ThriftKsDef(ksDef));
			return;
		} catch (Throwable e) {
			logger.error("Exception while creating keyspace, " + name
					+ " - probably already exists", e);
		}

		for (CfDef cfDef : cfDefList) {
			try {
				cluster.addColumnFamily(new ThriftCfDef(cfDef));
			} catch (Throwable e) {
				logger.error("Exception while creating CF, " + cfDef.getName()
						+ " - probably already exists", e);
			}
		}
	}

	public static java.util.UUID newTimeUUID() {
		com.eaio.uuid.UUID eaioUUID = new com.eaio.uuid.UUID();
		return new UUID(eaioUUID.time, eaioUUID.clockSeqAndNode);
	}

	/*
	 * Convenience methods for wrapping IndexedCollections methods
	 */

	public UUID createEntity(String type) {
		UUID id = newTimeUUID();
		createMutator(ko, ue).insert(id, IndexedCollections.DEFAULT_ITEM_CF,
				createColumn("type", type, se, se));
		return id;
	}

	public void addEntityToCollection(ContainerCollection<UUID> container,
			UUID itemEntity) {
		IndexedCollections.addItemToCollection(ko, container, itemEntity,
				IndexedCollections.defaultCFSet, ue);
	}

	public List<UUID> getEntitiesInCollection(
			ContainerCollection<UUID> container) {
		return IndexedCollections.getItemsInCollection(ko, container,
				IndexedCollections.defaultCFSet, ue);
	}

	public static <V> void setEntityColumn(UUID itemEntity, String columnName,
			V columnValue, Set<ContainerCollection<UUID>> containers,
			Serializer<V> valueSerializer) {
		IndexedCollections.setItemColumn(ko, itemEntity, columnName,
				columnValue, containers, IndexedCollections.defaultCFSet, ue,
				se, valueSerializer, ue);
	}

	public static List<UUID> searchContainer(
			ContainerCollection<UUID> container, String columnName,
			Object searchValue) {

		return IndexedCollections.searchContainer(ko, container, columnName,
				searchValue, null, 100, false, IndexedCollections.defaultCFSet,
				ue, ue, se);
	}

	public static List<UUID> searchContainer(
			ContainerCollection<UUID> container, String columnName,
			Object startValue, Object endValue, boolean inclusive) {

		return IndexedCollections.searchContainer(ko, container, columnName,
				startValue, endValue, inclusive, null, 100, false,
				IndexedCollections.defaultCFSet, ue, ue, se);
	}

}
