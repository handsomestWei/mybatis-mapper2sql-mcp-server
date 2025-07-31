#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MyBatis Mapper2SQL MCP Client Test Script
1、自动启动MCP服务器，获取进程ID
2、启动MCP客户端，通过stdio (stdin/stdout) 标准输入输出 方式实现进程间通信
"""

import json
import subprocess
import sys
import time
import os
import threading
import queue
from typing import Dict, Any

class MCPClient:
    def __init__(self):
        """
        初始化MCP客户端
        """
        self.server_process = None
        self.server_pid = None
        self.output_thread = None
        self.stop_monitoring = False
        self.response_queue = queue.Queue()

    def start_server(self):
        """启动MCP服务器"""
        print("启动MCP服务器...")

        # 构建启动命令 - 直接调用run-jar.bat
        script_dir = os.path.dirname(os.path.abspath(__file__))
        project_root = os.path.dirname(script_dir)
        run_jar_script = os.path.join(project_root, "scripts", "run-jar.bat")

        # 在Windows上使用cmd调用bat文件
        server_command = ['cmd', '/c', run_jar_script]

        try:
            # 启动服务器进程
            self.server_process = subprocess.Popen(
                server_command,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
                universal_newlines=True,
                encoding='utf-8',  # 明确指定UTF-8编码
                errors='replace',  # 遇到无法解码的字符时替换为占位符
                cwd=project_root  # 设置工作目录
            )
            self.server_pid = self.server_process.pid
            print(f"MCP服务器已启动，PID: {self.server_pid}")

            # 等待服务器完全启动，并实时显示输出
            print("等待服务器启动完成...")
            max_wait_time = 20  # 最大等待20秒
            start_time = time.time()
            server_ready = False

            while time.time() - start_time < max_wait_time:
                # 检查进程是否退出
                if self.server_process.poll() is not None:
                    print("服务器进程已退出，启动失败")
                    return False

                # 尝试读取服务器输出
                try:
                    # 简单等待一段时间，让服务器启动
                    time.sleep(8)
                    server_ready = True
                    print("服务器启动等待完成")
                    break
                except Exception as e:
                    # 如果读取失败，继续等待
                    pass

                time.sleep(0.5)  # 短暂等待

            if not server_ready:
                print("警告：未检测到服务器就绪信号，但继续测试...")

            print("服务器启动完成，准备开始测试")

            # 暂时禁用后台监控线程，避免与消息发送竞争
            # self.start_output_monitoring()

            return True
        except Exception as e:
            print(f"启动服务器失败: {e}")
            return False

    def start_output_monitoring(self):
        """启动后台线程监控服务器输出"""
        def monitor_output():
            while not self.stop_monitoring and self.server_process:
                try:
                    line = self.server_process.stdout.readline().strip()
                    if line:
                        print(f"[服务器监控] {line}")
                        # 尝试解析JSON响应并放入队列
                        try:
                            json_response = json.loads(line)
                            self.response_queue.put(json_response)
                        except json.JSONDecodeError:
                            # 非JSON输出，忽略
                            pass
                except Exception as e:
                    if not self.stop_monitoring:
                        print(f"[服务器监控] 读取错误: {e}")
                    break
                time.sleep(0.1)

        self.output_thread = threading.Thread(target=monitor_output, daemon=True)
        self.output_thread.start()
        print("[客户端] 启动服务器输出监控线程")

    def send_message(self, message: Dict[str, Any]) -> Dict[str, Any]:
        """
        发送JSON-RPC消息到服务器

        Args:
            message: 要发送的消息字典

        Returns:
            服务器的响应
        """
        if not self.server_process:
            raise RuntimeError("服务器未启动")

        # 发送消息
        message_str = json.dumps(message, ensure_ascii=False)
        print(f"发送消息: {message_str}")

        try:
            # 发送消息到服务器标准输入
            self.server_process.stdin.write(message_str + "\n")
            self.server_process.stdin.flush()

            # 等待并读取服务器响应
            print("[客户端] 等待服务器响应...")
            max_wait_time = 15  # 最大等待15秒
            start_time = time.time()

            while time.time() - start_time < max_wait_time:
                try:
                    # 直接从stdout读取响应
                    response = self.server_process.stdout.readline().strip()
                    if response:
                        print(f"[客户端] 收到响应: {response}")
                        # 尝试解析JSON响应
                        try:
                            json_response = json.loads(response)
                            # 检查是否是通知消息
                            if "method" in json_response and json_response["method"].startswith("notifications/"):
                                print(f"[客户端] 跳过通知消息: {json_response['method']}")
                                continue
                            # 检查是否是针对当前请求的响应
                            if "id" in json_response and json_response["id"] == message.get("id"):
                                print(f"[客户端] 找到对应响应，ID: {json_response['id']}")
                                return json_response
                            # 如果是没有ID的响应（如initialize的响应），也返回
                            elif "id" not in json_response and "result" in json_response:
                                print(f"[客户端] 找到结果响应")
                                return json_response
                            else:
                                print(f"[客户端] 跳过其他响应")
                                continue
                        except json.JSONDecodeError:
                            # 如果不是有效的JSON，继续读取
                            print(f"[客户端] 跳过非JSON响应")
                            continue
                    else:
                        # 没有响应，短暂等待
                        time.sleep(0.1)
                        continue
                except Exception as e:
                    print(f"[客户端] 读取响应时出错: {e}")
                    time.sleep(0.1)
                    continue

            print("[客户端] 等待响应超时")
            # 检查服务器进程是否还在运行
            if self.server_process.poll() is not None:
                return {"error": "server_died", "message": "服务器进程已退出"}
            else:
                return {"error": "timeout", "message": "等待服务器响应超时"}

        except Exception as e:
            print(f"发送消息时出错: {e}")
            return {"error": "send_failed", "message": str(e)}

    def stop_server(self):
        """停止MCP服务器"""
        # 停止监控线程（如果启用的话）
        if hasattr(self, 'output_thread') and self.output_thread:
            self.stop_monitoring = True
            if self.output_thread.is_alive():
                self.output_thread.join(timeout=2)
                print("[客户端] 服务器输出监控线程已停止")

        if self.server_process:
            self.server_process.terminate()
            self.server_process.wait()
            print("MCP服务器已停止")

    def test_initialize(self):
        """测试初始化连接"""
        message = {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                    "tools": {},
                    "resources": {},
                    "prompts": {}
                },
                "clientInfo": {
                    "name": "test-client",
                    "version": "1.0.0"
                }
            }
        }
        return self.send_message(message)

    def test_list_tools(self):
        """测试列出工具"""
        message = {
            "jsonrpc": "2.0",
            "id": 2,
            "method": "tools/list",
            "params": {}
        }
        return self.send_message(message)

    def test_parse_mapper(self, file_path: str):
        """测试解析mapper文件"""
        message = {
            "jsonrpc": "2.0",
            "id": 3,
            "method": "tools/call",
            "params": {
                "name": "parse_mapper",
                "arguments": {
                    "file_path": file_path
                }
            }
        }
        return self.send_message(message)

    def test_parse_mapper_with_mock(self, file_path: str):
        """测试带mock数据的解析"""
        message = {
            "jsonrpc": "2.0",
            "id": 4,
            "method": "tools/call",
            "params": {
                "name": "parse_mapper_with_mock",
                "arguments": {
                    "file_path": file_path
                }
            }
        }
        return self.send_message(message)

def main():
    """主函数"""
    print("MyBatis Mapper2SQL MCP Client Test")
    print("=" * 40)

    # 检查当前目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    os.chdir(project_root)

    print(f"当前目录: {os.getcwd()}")

    # 创建测试文件
    test_data_dir = "test-data"
    if not os.path.exists(test_data_dir):
        os.makedirs(test_data_dir)

    test_mapper_content = '''<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.test.dao.TestDao">
    <select id="selectUsers" resultType="com.test.entity.User">
        SELECT id, name, email, create_time
        FROM users
        WHERE name = #{name}
    </select>

    <insert id="insertUser" parameterType="com.test.entity.User">
        INSERT INTO users (name, email, create_time)
        VALUES (#{name}, #{email}, #{createTime})
    </insert>
</mapper>'''

    test_file_path = os.path.join(test_data_dir, "SimpleTestMapper.xml")
    with open(test_file_path, 'w', encoding='utf-8') as f:
        f.write(test_mapper_content)

    print(f"测试文件已创建: {test_file_path}")

    # 创建MCP客户端并启动服务器
    client = MCPClient()

    try:
        # 启动服务器
        if not client.start_server():
            print("启动服务器失败，退出测试")
            sys.exit(1)

        print("\n开始测试...")
        print("-" * 40)
        time.sleep(10)

        # Test 1: Initialize
        print("\n1. 测试初始化连接")
        response1 = client.test_initialize()
        print(f"初始化响应: {json.dumps(response1, indent=2, ensure_ascii=False)}")
        time.sleep(5)

        # Test 2: List tools
        print("\n2. 测试列出工具")
        response2 = client.test_list_tools()
        print(f"工具列表响应: {json.dumps(response2, indent=2, ensure_ascii=False)}")
        time.sleep(3)

        # Test 3: Parse mapper (if tools list successful)
        if "error" not in response2:
            print("\n3. 测试解析mapper文件")
            response3 = client.test_parse_mapper(test_file_path)
            print(f"解析mapper响应: {json.dumps(response3, indent=2, ensure_ascii=False)}")
            time.sleep(10)

            # Test 4: Parse with mock data
            print("\n4. 测试带mock数据的解析")
            response4 = client.test_parse_mapper_with_mock(test_file_path)
            print(f"带mock解析响应: {json.dumps(response4, indent=2, ensure_ascii=False)}")
            time.sleep(10)
        else:
            print("跳过后续测试，因为前面的测试失败")
            response3 = {"error": "previous_test_failed"}
            response4 = {"error": "previous_test_failed"}

        print("\n测试完成!")

    except KeyboardInterrupt:
        print("\n用户中断测试")
    except Exception as e:
        print(f"测试过程中出错: {e}")
    finally:
        # 停止服务器
        client.stop_server()

    # 清理测试文件
    try:
        if os.path.exists(test_file_path):
            os.remove(test_file_path)
            print(f"已删除测试文件: {test_file_path}")

        # 如果test-data目录为空，也删除它
        if os.path.exists(test_data_dir) and not os.listdir(test_data_dir):
            os.rmdir(test_data_dir)
            print(f"已删除空目录: {test_data_dir}")

    except Exception as e:
        print(f"清理文件时出错: {e}")

if __name__ == "__main__":
    main()
