package com.conversantmedia.mapreduce.example;

/*
 * #%L
 * Mara Framework Examples
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


import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.springframework.stereotype.Service;

import com.conversantmedia.mapreduce.tool.annotation.NamedOutput;

/**
 *
 *
 */
@Service
public class NamedOutputWordCountMapper extends Mapper<LongWritable, Text, Text, LongWritable> {

	private final static LongWritable ONE = new LongWritable(1);

	@NamedOutput("DEBUG")
	private MultipleOutputs<Text, Text> multiOut;

	private final Text word = new Text();

	@Override
	protected void map(LongWritable key, Text value, Context context)
			throws IOException, InterruptedException {
		String line = value.toString();
		StringTokenizer tokenizer = new StringTokenizer(line);
		while (tokenizer.hasMoreTokens()) {
			word.set(tokenizer.nextToken().replaceAll( "\\W", "" ));
			context.write(word, ONE);
		}

		// Debug output
		if (line.length() > 10) {
			multiOut.write("DEBUG", new Text(line.length() + ":"), value);
		}
	}
}