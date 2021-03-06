/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.metrics;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A metric of a given instance. The instance can be master, worker, or client.
 */
public final class Metric implements Serializable {
  private static final Logger LOG = LoggerFactory.getLogger(Metric.class);
  private static final long serialVersionUID = -2236393414222298333L;

  private static final String ID_SEPARATOR = "-id:";
  public static final String TAG_SEPARATOR = ":";
  private final MetricsSystem.InstanceType mInstanceType;
  private final String mHostname;
  private final String mName;
  private final Double mValue;
  private String mInstanceId;
  // TODO(yupeng): consider a dedicated data structure for tag, when more functionality are added to
  // tags in the future
  private final Map<String, String> mTags;

  /**
   * Constructs a {@link Metric} instance.
   *
   * @param instanceType the instance type
   * @param hostname the hostname
   * @param name the metric name
   * @param value the value
   */
  public Metric(MetricsSystem.InstanceType instanceType, String hostname, String name,
      Double value) {
    this(instanceType, hostname, null, name, value);
  }

  /**
   * Constructs a {@link Metric} instance.
   *
   * @param instanceType the instance type
   * @param hostname the hostname
   * @param id the instance id
   * @param name the metric name
   * @param value the value
   */
  public Metric(MetricsSystem.InstanceType instanceType, String hostname, String id, String name,
      Double value) {
    Preconditions.checkNotNull(name);
    mInstanceType = instanceType;
    mHostname = hostname;
    mInstanceId = id;
    mName = name;
    mValue = value;
    mTags = new LinkedHashMap<>();
  }

  /**
   * @return the instance type
   */
  public MetricsSystem.InstanceType getInstanceType() {
    return mInstanceType;
  }

  /**
   * Adds a new tag. If the tag name already exists, it will be replaced.
   *
   * @param name the tag name
   * @param value the tag value
   */
  public void addTag(String name, String value) {
    mTags.put(name, value);
  }

  /**
   * @return the hostname
   */
  public String getHostname() {
    return mHostname;
  }

  /**
   * @return the metric name
   */
  public String getName() {
    return mName;
  }

  /**
   * @return the metric value
   */
  public double getValue() {
    return mValue;
  }

  /**
   * @return the instance id
   */
  public String getInstanceId() {
    return mInstanceId;
  }

  /**
   * @return the tags
   */
  public Map<String, String> getTags() {
    return mTags;
  }

  /**
   * Sets the instance id.
   *
   * @param instanceId the instance id;
   */
  public void setInstanceId(String instanceId) {
    mInstanceId = instanceId;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    if (!(other instanceof Metric)) {
      return false;
    }
    Metric metric = (Metric) other;
    return Objects.equal(getFullMetricName(), metric.getFullMetricName())
        && Objects.equal(mValue, metric.mValue);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getFullMetricName(), mValue);
  }

  /**
   * @return the fully qualified metric name, which is of pattern
   *         instance.[hostname-id:instanceId.]name[.tagName:tagValue]*, where the tags are appended
   *         at the end
   */
  public String getFullMetricName() {
    StringBuilder sb = new StringBuilder();
    sb.append(mInstanceType).append('.');
    if (mHostname != null) {
      sb.append(mHostname);
      if (mInstanceId != null) {
        sb.append(ID_SEPARATOR).append(mInstanceId);
      }
      sb.append('.');
    }

    sb.append(mName);
    for (Entry<String, String> entry : mTags.entrySet()) {
      sb.append('.' + entry.getKey() + TAG_SEPARATOR + entry.getValue());
    }
    return sb.toString();
  }

  /**
   * @return the thrift object it converts to. Note the value must be either integer or long
   */
  public alluxio.thrift.Metric toThrift() {
    alluxio.thrift.Metric metric = new alluxio.thrift.Metric();
    metric.setInstance(mInstanceType.toString());
    metric.setHostname(mHostname);
    metric.setName(mName);
    metric.setInstanceId(mInstanceId);
    metric.setValue(mValue);
    metric.setTags(mTags);
    return metric;
  }

  /**
   * Gets the metric name with the appendix of tags. The returned name is of the pattern
   * name[.tagName:tagValue]*.
   *
   * @param name the metric name
   * @param tags the tag name and tag value pairs
   * @return the name with the tags appended
   */
  public static String getMetricNameWithTags(String name, String... tags) {
    Preconditions.checkArgument(tags.length % 2 == 0,
        "The tag arguments should be tag name and tag value pairs");
    StringBuilder sb = new StringBuilder();
    sb.append(name);
    for (int i = 0; i < tags.length; i += 2) {
      sb.append('.' + tags[i] + TAG_SEPARATOR + tags[i + 1]);
    }
    return sb.toString();
  }

  /**
   * Creates the metric from the full name and the value.
   *
   * @param fullName the full name
   * @param value the value
   * @return the created metric
   */
  public static Metric from(String fullName, double value) {
    String[] pieces = fullName.split("\\.");
    Preconditions.checkArgument(pieces.length > 1, "Incorrect metrics name: %s.", fullName);

    String hostname = null;
    String id = null;
    String name = null;
    int tagStartIdx = 0;
    // Master or cluster metrics don't have hostname included.
    if (pieces[0].equals(MetricsSystem.InstanceType.MASTER.toString())
        || pieces[0].equals(MetricsSystem.CLUSTER.toString())) {
      name = pieces[1];
      tagStartIdx = 2;
    } else {
      if (pieces[1].contains(ID_SEPARATOR)) {
        String[] ids = pieces[1].split(ID_SEPARATOR);
        hostname = ids[0];
        id = ids[1];
      } else {
        hostname = pieces[1];
      }
      name = pieces[2];
      tagStartIdx = 3;
    }
    MetricsSystem.InstanceType instance = MetricsSystem.InstanceType.fromString(pieces[0]);
    Metric metric = new Metric(instance, hostname, id, name, value);

    // parse tags
    for (int i = tagStartIdx; i < pieces.length; i++) {
      String tagStr = pieces[i];
      if (!tagStr.contains(TAG_SEPARATOR)) {
        LOG.warn("Unknown tag {}, the tag format should be tagname{}tagvalue", pieces[i],
            TAG_SEPARATOR);
        continue;
      }
      int tagSeparatorIdx = tagStr.indexOf(TAG_SEPARATOR);
      metric.addTag(tagStr.substring(0, tagSeparatorIdx), tagStr.substring(tagSeparatorIdx + 1));
    }
    return metric;
  }

  /**
   * Constructs the metric object from the thrift format.
   *
   * @param metric the metric in thrift format
   * @return the constructed metric
   */
  public static Metric from(alluxio.thrift.Metric metric) {
    Metric created = new Metric(MetricsSystem.InstanceType.fromString(metric.getInstance()),
        metric.getHostname(), metric.getInstanceId(), metric.getName(), metric.getValue());
    for (Entry<String, String> entry : metric.getTags().entrySet()) {
      created.addTag(entry.getKey(), entry.getValue());
    }
    return created;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("instanceType", mInstanceType)
        .add("hostname", mHostname).add("instanceId", mInstanceId).add("name", mName)
        .add("value", mValue).add("tags", mTags).toString();
  }
}
