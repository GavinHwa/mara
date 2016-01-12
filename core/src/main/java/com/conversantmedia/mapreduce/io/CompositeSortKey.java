package com.conversantmedia.mapreduce.io;

/*
 * #%L
 * Mara Core framework
 * ~~
 * Copyright (C) 2015 Conversant
 * ~~
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
 * #L%
 */


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.util.ReflectionUtils;

/**
 *
 * @param <G> Grouping/Partitioning key type
 * @param <S> Sorting key type
 */
public class CompositeSortKey<G extends WritableComparable, S extends WritableComparable>
	implements WritableComparable<CompositeSortKey<G, S>> {

	// The key to use for grouping and partitioning our keys into the
	// reduce phase.
	private G groupKey;

	// The key for sorting the keys.
	private S sortKey;

	public CompositeSortKey() {}

    public CompositeSortKey(CompositeSortKey<G, S> copyFrom) {
        this(copyFrom.groupKey, copyFrom.sortKey);
    }
	
	public CompositeSortKey(G groupKey, S sortKey) {
		this.groupKey = groupKey;
		this.sortKey = sortKey;
	}

	public G getGroupKey() {
		return groupKey;
	}

	public void setGroupKey(G groupKey) {
		this.groupKey = groupKey;
	}

	public S getSortKey() {
		return sortKey;
	}

	public void setSortKey(S sortKey) {
		this.sortKey = sortKey;
	}


	@Override
	public void write(DataOutput out) throws IOException {
		this.getGroupKey().write(out);
		this.getSortKey().write(out);
	}

	@Override
	public void readFields(DataInput in) throws IOException {
		this.getGroupKey().readFields(in);
		this.getSortKey().readFields(in);
	}

	/**
	 * Natural order of group, then natural order of sort key.
	 *
	 * @param that the other key
	 * @return negative integer, zero, or a positive integer as per contract
	 */
	@SuppressWarnings("unchecked")
	@Override
	public int compareTo(CompositeSortKey<G, S> that) {
		int compare = this.groupKey.compareTo(that.groupKey);
		if (compare == 0) {
			compare = this.sortKey.compareTo(that.sortKey);
		}
		return compare;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ (groupKey == null ? 0 : groupKey.hashCode());
		result = prime * result + (sortKey == null ? 0 : sortKey.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		CompositeSortKey other = (CompositeSortKey) obj;
		if (groupKey == null) {
			if (other.groupKey != null) {
				return false;
			}
		} else if (!groupKey.equals(other.groupKey)) {
			return false;
		}
		if (sortKey == null) {
			if (other.sortKey != null) {
				return false;
			}
		} else if (!sortKey.equals(other.sortKey)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return this.getClass().getName() + "[" + this.groupKey.toString()
				+ ", " + this.sortKey.toString() + "]";
	}

	/**
	 * Comparator for sorting the composite key based on the natural order of both the partition (group) key
	 * and then the sort key.
	 */
	public static class NaturalSortComparator<G extends WritableComparable, S extends WritableComparable> extends CompositeSortKeyComparator<G, S> {
		@Override
		public int compare(CompositeSortKey<G, S> key1, CompositeSortKey<G, S> key2) {
			return key1.compareTo(key2);
		}
	}

	/**
	 * Comparator for sorting the composite key based on the natural order of the partition (group) key
	 * and then the reverse order for the sort key.
	 */
	public static final class ReverseSortComparator<G extends WritableComparable, S extends WritableComparable> extends CompositeSortKeyComparator<G, S> {

		@SuppressWarnings("unchecked")
		@Override
		public int compare(CompositeSortKey<G, S> key1, CompositeSortKey<G, S> key2) {
			int compare = key1.groupKey.compareTo(key2.groupKey);
			if (compare == 0) {
				compare = key1.sortKey.compareTo(key2.sortKey) * -1;
			}
			return compare;
		}
	}

	/**
	 * Comparator for grouping based on the natural ordering  of the partition (group) key
	 */
	public static final class GroupingComparator<G extends WritableComparable,
			S extends WritableComparable> extends CompositeSortKeyComparator<G, S> {

		@SuppressWarnings("unchecked")
		@Override
		public int compare(CompositeSortKey<G, S> key1, CompositeSortKey<G, S> key2) {
			return key1.groupKey.compareTo(key2.groupKey);
		}
	}

	/**
	 * The default partitioner for this composite key.
	 * 
	 * @param <T>	the value type
	 */
	public static final class KeyPartitioner<T> extends Partitioner<CompositeSortKey, T> {
		@Override
		public int getPartition(CompositeSortKey key, T value, int numPartitions) {
			// Need to protected against negative numbers and Integer.MIN_VALUE
			return (key.getGroupKey().hashCode() & Integer.MAX_VALUE) % numPartitions;
		}
	}

	/**
	 * This class handles key creation for eventual sorting by partition and sort keys
	 *
	 * @param <G> Partition or Group Key
	 * @param <S> Sort Key
	 */
	public abstract static class CompositeSortKeyComparator<G extends WritableComparable,
		S extends WritableComparable> extends Configured implements RawComparator<CompositeSortKey<G, S>> {

		private CompositeSortKey<G, S> key1;
		private CompositeSortKey<G, S> key2;
		private final DataInputBuffer buffer;

		private Class<G> groupKeyClass;
		private Class<S> sortKeyClass;

		public CompositeSortKeyComparator() {
			buffer = new DataInputBuffer();
		}

		public CompositeSortKey<G, S> newKey() {
			CompositeSortKey<G, S> key = new CompositeSortKey<>();
			key.setGroupKey(ReflectionUtils.newInstance(groupKeyClass, null));
			key.setSortKey(ReflectionUtils.newInstance(sortKeyClass, null));
			return key;
		}

		@Override @SuppressWarnings("unchecked")
		public void setConf(Configuration conf) {
			// Configured default constructor sets to 'null'
			if (conf != null) {
				this.groupKeyClass = (Class<G>) conf.getClass(CompositeSortKeySerialization.CONF_KEY_GROUPKEY_CLASS, null);
				this.sortKeyClass = (Class<S>) conf.getClass(CompositeSortKeySerialization.CONF_KEY_SORTKEY_CLASS, null);
				key1 = newKey();
				key2 = newKey();
			}
			super.setConf(conf);
		}

		@Override
		public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
			try {
				buffer.reset(b1, s1, l1);
				  key1.readFields(buffer);

				  buffer.reset(b2, s2, l2);
				  key2.readFields(buffer);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return compare(key1, key2);
		}

		@Override
		public abstract int compare(CompositeSortKey<G, S> o1, CompositeSortKey<G, S> o2);

	}
}
