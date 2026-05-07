package com.yupi.yuaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class AiChatAgentTest {

    @Resource
    private AiChatAgent aiChatAgent;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是程序员老6";
        String answer = aiChatAgent.doChat(message, chatId);
        // 第二轮
        message = "我目前在一家互联网公司做后端开发，想了解如何提升晋升竞争力";
        answer = aiChatAgent.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮
        message = "我目前的职位是什么来着？刚跟你说过，帮我回忆一下";
        answer = aiChatAgent.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是程序员老6，我在公司做后端开发两年了，想晋升为高级工程师，但不知道该如何规划";
        AiChatAgent.AiChatReport aiChatReport = aiChatAgent.doChatWithReport(message, chatId);
        Assertions.assertNotNull(aiChatReport);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经晋升为团队负责人了，但感觉很难快速适应管理角色，怎么办？";
        String answer = aiChatAgent.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("最近互联网大厂后端开发岗位的薪资水平大概是多少？");

        // 测试网页抓取：职场案例分析
        testMessage("最近和上级沟通不顺畅，看看网站（www.baidu.com）有没有相关的职场沟通技巧？");

        // 测试资源下载：图片下载
        testMessage("直接下载一张适合做职场桌面壁纸的图片为文件");

        // 测试终端操作：执行代码
        testMessage("执行 Python3 脚本来生成数据分析报告");

        // 测试文件操作：保存用户档案
        testMessage("保存我的职场档案为文件");

        // 测试 PDF 生成
        testMessage("生成一份'年度晋升计划'PDF，包含目标设定、能力提升路径和关键里程碑");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = aiChatAgent.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        // 测试地图 MCP
//        String message = "我在上海静安区上班，请帮我找到 5 公里内适合商务宴请的餐厅";
//        String answer =  aiChat.doChatWithMcp(message, chatId);
//        Assertions.assertNotNull(answer);
        // 测试图片搜索 MCP
        String message = "帮我搜索一些职场办公效率提升的相关图片";
        String answer = aiChatAgent.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
