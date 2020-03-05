package io.confluent.developer;

import org.apache.kafka.common.serialization.DoubleDeserializer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import io.confluent.demo.CountAndSum;
import io.confluent.demo.Rating;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import lombok.extern.slf4j.Slf4j;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@Slf4j
public class RunningAverageTest {

  private static final String RATINGS_TOPIC_NAME = "ratings";
  private static final String AVERAGE_RATINGS_TOPIC_NAME = "average-ratings";
  private static final Rating LETHAL_WEAPON_RATING_10 = new Rating(362L, 10.0);
  // private static final Rating LETHAL_WEAPON_RATING_9 = new Rating(362L,9.0);
  private static final Rating LETHAL_WEAPON_RATING_8 = new Rating(362L, 8.0);
  private TopologyTestDriver testDriver;
  private SpecificAvroSerde<Rating> ratingSpecificAvroSerde;

  @Before
  public void setUp() throws IOException, RestClientException {

    final Properties properties = new Properties();
    properties.put("application.id", "kafka-movies-test");
    properties.put("bootstrap.servers", "DUMMY_KAFKA_CONFLUENT_CLOUD_9092");
    properties.put("schema.registry.url", "DUMMY_SR_CONFLUENT_CLOUD_8080");
    properties.put("default.topic.replication.factor", "1");
    properties.put("offset.reset.policy", "latest");

    final RunningAverage streamsApp = new RunningAverage();
    final Properties streamsConfig = streamsApp.buildStreamsProperties(properties);

    StreamsBuilder builder = new StreamsBuilder();

    final Map<String, String> mockSerdeConfig = RunningAverage.getSerdeConfig(properties);

    SpecificAvroSerde<CountAndSum> countAndSumSerde = new SpecificAvroSerde<>(new MockSchemaRegistryClient());
    countAndSumSerde.configure(mockSerdeConfig, false);

    // MockSchemaRegistryClient doesn't require connection to Schema Registry which is perfect for unit test
    final MockSchemaRegistryClient client = new MockSchemaRegistryClient();
    client.register(RATINGS_TOPIC_NAME + "-value", Rating.SCHEMA$);
    ratingSpecificAvroSerde = new SpecificAvroSerde<>(client);
    countAndSumSerde.configure(mockSerdeConfig, false);

    KStream<Long, Rating>
        ratingStream =
        builder.stream(RATINGS_TOPIC_NAME, Consumed.with(Serdes.Long(), ratingSpecificAvroSerde));
    final KTable<Long, Double> ratingAverageTable = RunningAverage.getRatingAverageTable(ratingStream,
                                                                                         AVERAGE_RATINGS_TOPIC_NAME,
                                                                                         countAndSumSerde);

    final Topology topology = builder.build();
    testDriver = new TopologyTestDriver(topology, streamsConfig);

  }

  @Test
  public void validateIfTestDriverCreated() {
    assertNotNull(testDriver);
  }

  @Test
  public void validateAverageRating() {

    TestInputTopic<Long, Rating> inputTopic = testDriver.createInputTopic(RATINGS_TOPIC_NAME,
                                                                          new LongSerializer(),
                                                                          ratingSpecificAvroSerde.serializer());

    inputTopic.pipeInput(LETHAL_WEAPON_RATING_10);
    inputTopic.pipeInput(LETHAL_WEAPON_RATING_8);

    final TestOutputTopic<Long, Double> outputTopic = testDriver.createOutputTopic(AVERAGE_RATINGS_TOPIC_NAME,
                                                                                   new LongDeserializer(),
                                                                                   new DoubleDeserializer());

    final KeyValue<Long, Double> longDoubleKeyValue = outputTopic.readKeyValue();
    assertThat(longDoubleKeyValue,
               equalTo(new KeyValue<>(362L, 9.0)));

    final KeyValueStore<Long, Double>
        keyValueStore =
        testDriver.getKeyValueStore("average-ratings");
    final Double expected = keyValueStore.get(362L);
    Assert.assertEquals("Message", expected, 9.0, 0.0);
  }

  @After
  public void tearDown() {
    testDriver.close();
  }
}