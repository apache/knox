# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import requests
import docker

class Knox:

    def __init__(self, container_name):
        """
        :param container_name:
        """
        client = docker.from_env()
        self.knox_container = client.containers.get(container_name)

    def run_knox_cmd(self, knox_cmd):
        """
        :param knox_cmd:
        :return:
        """
        result = self.knox_container.exec_run(f"{knox_cmd}", stream=False)
        output = result.output.decode()
        print(f"\n[CMD={knox_cmd}] OUTPUT:\n{output}")
        return output

