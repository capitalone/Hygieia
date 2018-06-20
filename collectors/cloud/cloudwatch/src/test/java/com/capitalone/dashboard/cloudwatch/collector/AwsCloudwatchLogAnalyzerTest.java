package com.capitalone.dashboard.cloudwatch.collector;

import com.capitalone.dashboard.model.CollectorType;
import org.junit.Test;

import static org.assertj.core.api.Java6Assertions.assertThat;


/**
 * Created by stevegal on 16/06/2018.
 */
public class AwsCloudwatchLogAnalyzerTest {

    @Test
    public void createsPrototype(){
        AwsCloudwatchLogAnalyzer producedCollector = AwsCloudwatchLogAnalyzer.prototype();

        assertThat(producedCollector.getName()).isEqualTo("CloudwatchLogAnalyzer");
        assertThat(producedCollector.getCollectorType()).isEqualTo(CollectorType.Log);
        assertThat(producedCollector.isEnabled()).isTrue();
        assertThat(producedCollector.isOnline()).isTrue();
    }
}