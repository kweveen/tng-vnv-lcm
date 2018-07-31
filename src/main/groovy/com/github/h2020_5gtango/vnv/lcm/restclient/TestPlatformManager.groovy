/*
 * Copyright (c) 2015 SONATA-NFV, 2017 5GTANGO [, ANY ADDITIONAL AFFILIATION]
 * ALL RIGHTS RESERVED.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Neither the name of the SONATA-NFV, 5GTANGO [, ANY ADDITIONAL AFFILIATION]
 * nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * This work has been performed in the framework of the SONATA project,
 * funded by the European Commission under Grant number 671517 through
 * the Horizon 2020 and 5G-PPP programmes. The authors would like to
 * acknowledge the contributions of their colleagues of the SONATA
 * partner consortium (www.sonata-nfv.eu).
 *
 * This work has been performed in the framework of the 5GTANGO project,
 * funded by the European Commission under Grant number 761493 through
 * the Horizon 2020 and 5G-PPP programmes. The authors would like to
 * acknowledge the contributions of their colleagues of the 5GTANGO
 * partner consortium (www.5gtango.eu).
 */

package com.github.h2020_5gtango.vnv.lcm.restclient

import com.github.h2020_5gtango.vnv.lcm.model.NsRequest
import com.github.h2020_5gtango.vnv.lcm.model.NsResponse
import com.github.h2020_5gtango.vnv.lcm.model.TestPlan
import groovy.util.logging.Log
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
@Log
class TestPlatformManager {

    @Autowired
    @Qualifier('restTemplateWithAuth')
    RestTemplate restTemplate

    @Value('${app.tpm.ns.deploy.endpoint}')
    def nsDeployEndpoint

    @Value('${app.tpm.ns.status.endpoint}')
    def nsStatusEndpoint

    @Value('${app.tpm.ns.status.timeout.in.seconds}')
    int nsStatusTimeoutInSeconds

    @Value('${app.tpm.ns.status.ping.in.seconds}')
    int nsStatusPingInSeconds

    @Value('${app.tpm.ns.destroy.endpoint}')
    def nsDestroyEndpoint

    TestPlan deployNsForTest(TestPlan testPlan) {
        String serviceUuid = testPlan.networkServiceInstances.first().serviceUuid
        NsResponse response = findReadyNs(serviceUuid)
        if (!response) {
            def createRequest = new NsRequest(
                    serviceUuid: testPlan.networkServiceInstances.first().serviceUuid,
                    requestType: 'CREATE_SERVICE',
            )
            response = restTemplate.postForEntity(nsDeployEndpoint, createRequest, NsResponse).body
            def numberOfRetries = nsStatusTimeoutInSeconds / nsStatusPingInSeconds
            for (int i = 0; i < numberOfRetries; i++) {
                if (['ERROR', 'READY'].contains(response.status)) {
                    break
                }
                response = restTemplate.getForEntity(nsStatusEndpoint, NsResponse, response.id).body
                Thread.sleep(nsStatusPingInSeconds * 1000)
            }
        }

        testPlan.networkServiceInstances.first().status = response.status
        if (response.status == 'READY') {
            testPlan.networkServiceInstances.first().instanceUuid = response.instanceUuid
            testPlan.status = 'NS_DEPLOYED'
        } else {
            log.warning("Deploy NS failed with status $response.status")
            testPlan.status = 'NS_DEPLOY_FAILED'
        }
        testPlan
    }

    NsResponse findReadyNs(String serviceUuid) {
        NsResponse response
        restTemplate.getForEntity(nsDeployEndpoint, NsResponse[].class).body.each { r ->
            if (r.serviceUuid == serviceUuid && r.status == 'READY') {
                response = r
            }
        }
        response
    }

    TestPlan destroyNsAfterTest(TestPlan testPlan) {
        def terminateRequest = new NsRequest(
                instanceUuid: testPlan.networkServiceInstances.first().instanceUuid,
                requestType: 'TERMINATE',
        )
        //NsResponse response = restTemplate.postForEntity(nsDestroyEndpoint, terminateRequest, NsResponse).body
        testPlan.networkServiceInstances.first().status = 'TERMINATED'
        testPlan
    }
}
