import re
import asyncio
import concurrent.futures
from flask import Flask, request, render_template, jsonify, redirect, url_for, session
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address
from selenium.webdriver.chrome.options import Options
from selenium import webdriver
from bs4 import BeautifulSoup
import requests
import random
import time
import logging
import tls_client
from PIL import Image
import pytesseract
import json
import os
import sqlite3
from collections import defaultdict
from flask_bcrypt import Bcrypt
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart

app = Flask(__name__)
app.secret_key = 'session_key'
bcrypt = Bcrypt(app)

# 检查user_agents.txt文件是否存在
if not os.path.exists('user_agents.txt'):
    raise FileNotFoundError("user_agents.txt文件未找到，请创建该文件并包含常用User - Agent。")

# 代理 API 链接，需要替换为实际的 API 链接
PROXY_API_URL = 'https://your-proxy-api-url.com'

# 请求频率限制
limiter = Limiter(
    app=app,
    key_func=get_remote_address,
    default_limits=["5 per minute"]
)

# 日志配置
logging.basicConfig(filename='crawler.log', level=logging.INFO)

# 系统配置
config = {
    "proxy_api_url": PROXY_API_URL,
    "max_retries": 3,
    "request_delay_min": 5,
    "request_delay_max": 10,
    "email_sender": "your_email@example.com",
    "email_password": "your_email_password"
}

# 流量监控
traffic_monitor = defaultdict(int)

# 异常报警
alerts = []


class AntiScrapingSystem:
    """综合反爬解决方案"""

    def __init__(self):
        self.user_agents = self._load_user_agents()
        self.browser_fingerprints = self._generate_fingerprints()

    def _load_user_agents(self):
        """加载预定义的User - Agent列表"""
        try:
            with open('user_agents.txt') as f:
                return [line.strip() for line in f]
        except Exception as e:
            logging.error(f"加载User - Agent列表失败: {str(e)}")
            return []

    def _generate_fingerprints(self):
        """生成浏览器指纹"""
        return {
            'webgl_vendor': 'Google Inc.',
            'canvas_hash': str(random.randint(100000, 999999)),
            'timezone': 'Asia/Shanghai'
        }

    def get_headers(self):
        """生成动态请求头"""
        return {
            'User-Agent': random.choice(self.user_agents) if self.user_agents else 'Mozilla/5.0',
            'Accept-Encoding': 'gzip, deflate, br',
            'Accept-Language': 'zh - CN,zh;q = 0.9',
            'Referer': 'https://www.google.com',
            'DNT': '1',
            'X-Requested-With': 'XMLHttpRequest',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed - exchange;v=b3;q=0.9',
            'Sec-Fetch-Site': 'same - origin',
            'Sec-Fetch-Mode': 'navigate',
            'Sec-Fetch-Dest': 'document'
        }

    def get_tls_client(self):
        """创建TLS指纹客户端"""
        return tls_client.Session(
            client_identifier="chrome_110",
            random_tls_extension_order=True
        )


class ProxyManager:
    """代理管理类"""

    def __init__(self):
        self.proxies = self.get_proxies_from_api()
        self.current_proxy = None

    def get_proxies_from_api(self):
        """从 API 链接获取代理 IP"""
        try:
            response = requests.get(config["proxy_api_url"])
            if response.status_code == 200:
                # 假设 API 返回的是 JSON 格式，且包含一个 'proxies' 字段
                proxy_list = response.json().get('proxies', [])
                formatted_proxies = []
                for proxy in proxy_list:
                    ip = proxy.get('ip')
                    port = proxy.get('port')
                    if ip and port:
                        formatted_proxies.append(f'http://{ip}:{port}')
                return formatted_proxies
            else:
                logging.error(f"获取代理 IP 失败，状态码: {response.status_code}")
        except Exception as e:
            logging.error(f"获取代理 IP 时出错: {str(e)}")
        return []

    def get_proxy(self):
        if self.proxies:
            self.current_proxy = random.choice(self.proxies)
            return {
                'http': self.current_proxy,
                'https': self.current_proxy
            }
        return None


class CaptchaSolver:
    """验证码处理系统"""

    def __init__(self, api_key):
        self.api_key = api_key

    def solve_image_captcha(self, image_path):
        """使用OCR识别验证码"""
        try:
            image = Image.open(image_path)
            text = pytesseract.image_to_string(image)
            return text.strip()
        except Exception as e:
            logging.error(f"OCR失败: {str(e)}")
            return None

    def solve_advanced_captcha(self, site_key, url):
        """使用第三方API解决复杂验证码"""
        # 这里需要集成具体验证码服务商的API
        return "captcha_solution"


def check_robots_txt(url):
    """检查robots.txt"""
    try:
        domain = '/'.join(url.split('/')[:3])
        robots_url = f"{domain}/robots.txt"
        response = requests.get(robots_url, timeout=5)
        if response.status_code == 200:
            return response.text
        return None
    except Exception as e:
        logging.warning(f"无法获取robots.txt: {str(e)}")
        return None


# 初始化数据库
def init_db():
    conn = sqlite3.connect('crawler.db')
    c = conn.cursor()
    c.execute('''CREATE TABLE IF NOT EXISTS pages
                 (id INTEGER PRIMARY KEY AUTOINCREMENT,
                 url TEXT NOT NULL,
                 title TEXT,
                 content TEXT,
                 links TEXT)''')
    c.execute('''CREATE TABLE IF NOT EXISTS users
                 (id INTEGER PRIMARY KEY AUTOINCREMENT,
                 username TEXT NOT NULL UNIQUE,
                 password TEXT NOT NULL)''')
    conn.commit()
    conn.close()


# 存储数据到数据库
def save_to_db(url, title, content, links):
    links_str = json.dumps(links)
    conn = sqlite3.connect('crawler.db')
    c = conn.cursor()
    try:
        c.execute("INSERT INTO pages (url, title, content, links) VALUES (?,?,?,?)", (url, title, content, links_str))
        conn.commit()
    except Exception as e:
        print(f"Error saving to database: {e}")
        conn.rollback()
    finally:
        c.close()
        conn.close()


# 检查用户输入的 URL 是否合法
def is_valid_url(url):
    regex = re.compile(
        r'^(?:http|ftp)s?://'
        r'(?:(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\.)+(?:[A-Z]{2,6}\.?|[A-Z0-9-]{2,}\.?)|'
        r'localhost|'
        r'\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})'
        r'(?::\d+)?'
        r'(?:/?|[/?]\S+)$', re.IGNORECASE)
    return re.match(regex, url) is not None


# 异步爬取函数
async def async_crawl(url):
    loop = asyncio.get_running_loop()
    with concurrent.futures.ThreadPoolExecutor() as pool:
        result = await loop.run_in_executor(pool, crawl_single_url, url)
        return result


# 单 URL 爬取函数
def crawl_single_url(url):
    MAX_RETRIES = config["max_retries"]
    anti_scraping = AntiScrapingSystem()
    proxy_mgr = ProxyManager()
    captcha_solver = CaptchaSolver('your_captcha_api_key')

    for retry in range(MAX_RETRIES):
        session = anti_scraping.get_tls_client()
        time.sleep(random.uniform(config["request_delay_min"], config["request_delay_max"]))
        proxy = proxy_mgr.get_proxy()
        try:
            response = session.get(
                url,
                headers=anti_scraping.get_headers(),
                proxy=proxy
            )
            # 流量监控
            traffic_monitor[url] += len(response.content)
            break
        except Exception as e:
            if retry < MAX_RETRIES - 1:
                logging.warning(f"请求失败，尝试第 {retry + 2} 次，代理: {proxy}, 错误信息: {str(e)}")
            else:
                logging.error(f"请求失败，已达到最大重试次数，代理: {proxy}, 错误信息: {str(e)}")
                # 异常报警
                alerts.append(f"请求 {url} 失败: {str(e)}")
                send_alert_email(f"请求 {url} 失败: {str(e)}")
                return {'error': f"请求失败: {str(e)}"}

    if 'captcha' in response.text.lower():
        captcha_solution = captcha_solver.solve_advanced_captcha('site_key', url)
        # 提交验证码并重新请求...

    if 'required JavaScript' in response.text:
        chrome_options = Options()
        chrome_options.add_argument("--headless")
        chrome_options.add_argument(f"user - agent={anti_scraping.get_headers()['User - Agent']}")
        try:
            driver = webdriver.Chrome(options=chrome_options)
            driver.get(url)
            time.sleep(random.uniform(2, 5))
            content = driver.page_source
            driver.quit()
        except Exception as e:
            logging.error(f"JavaScript渲染失败: {str(e)}")
            # 异常报警
            alerts.append(f"JavaScript渲染 {url} 失败: {str(e)}")
            send_alert_email(f"JavaScript渲染 {url} 失败: {str(e)}")
            return {'error': f"JavaScript渲染失败: {str(e)}"}
    else:
        content = response.text

    soup = BeautifulSoup(content, 'html.parser')
    data = {
        'title': soup.title.string if soup.title else '无标题',
        'content': ' '.join([p.get_text() for p in soup.find_all('p')]),
        'links': [a['href'] for a in soup.find_all('a', href=True)],
        'fingerprint': anti_scraping.browser_fingerprints
    }

    save_to_db(url, data['title'], data['content'], data['links'])
    return data


# 发送报警邮件
def send_alert_email(message):
    sender_email = config["ypc2021121145@163.com"]
    receiver_email = "coolaphid@163.com"
    password = config["2021121145Ypc"]

    msg = MIMEMultipart()
    msg['From'] = sender_email
    msg['To'] = receiver_email
    msg['Subject'] = "爬虫系统异常报警"

    msg.attach(MIMEText(message, 'plain'))

    try:
        server = smtplib.SMTP('smtp.163.com',25)
        server.starttls()
        server.login(sender_email, password)
        text = msg.as_string()
        server.sendmail(sender_email, receiver_email, text)
        server.quit()
        logging.info("报警邮件发送成功")
    except Exception as e:
        logging.error(f"报警邮件发送失败: {str(e)}")


# 用户注册
@app.route('/register', methods=['GET', 'POST'])
def register():
    if request.method == 'POST':
        username = request.form.get('username')
        password = request.form.get('password')

        if not username or not password:
            return render_template('register.html', error="用户名和密码不能为空")

        hashed_password = bcrypt.generate_password_hash(password).decode('utf-8')

        try:
            conn = sqlite3.connect('crawler.db')
            c = conn.cursor()
            c.execute("INSERT INTO users (username, password) VALUES (?,?)", (username, hashed_password))
            conn.commit()
            conn.close()
            return redirect(url_for('login'))
        except sqlite3.IntegrityError:
            return render_template('register.html', error="用户名已存在")

    return render_template('register.html')


# 用户登录
@app.route('/login', methods=['GET', 'POST'])
def login():
    if request.method == 'POST':
        username = request.form.get('username')
        password = request.form.get('password')

        conn = sqlite3.connect('crawler.db')
        c = conn.cursor()
        c.execute("SELECT password FROM users WHERE username =?", (username,))
        user = c.fetchone()
        conn.close()

        if user and bcrypt.check_password_hash(user[0], password):
            session['username'] = username
            return redirect(url_for('index'))
        else:
            return render_template('login.html', error="用户名或密码错误")

    return render_template('login.html')


@app.route('/', methods=['GET'])
def index():
    if 'username' not in session:
        return redirect(url_for('login'))
    return render_template('index.html')


@app.route('/crawl', methods=['POST'])
@limiter.limit("3 per minute")
async def crawl():
    urls = request.json.get('urls')
    if not urls:
        return jsonify({'error': '未提供有效的URL列表'}), 400

    valid_urls = []
    for url in urls:
        if not isinstance(url, str) or not is_valid_url(url):
            return jsonify({'error': f'无效的URL: {url}'}), 400
        valid_urls.append(url)

    tasks = [async_crawl(url) for url in valid_urls]
    results = await asyncio.gather(*tasks)
    return jsonify(results)


@app.route('/admin', methods=['GET', 'POST'])
def admin():
    if 'username' not in session:
        return redirect(url_for('login'))
    if request.method == 'POST':
        # 更新系统配置
        config["proxy_api_url"] = request.form.get("proxy_api_url")
        config["max_retries"] = int(request.form.get("max_retries"))
        config["request_delay_min"] = float(request.form.get("request_delay_min"))
        config["request_delay_max"] = float(request.form.get("request_delay_max"))
        return redirect(url_for('admin'))

    # 查询数据库中的爬取结果
    conn = sqlite3.connect('crawler.db')
    c = conn.cursor()
    c.execute("SELECT url, title, content, links FROM pages")
    results = c.fetchall()
    conn.close()

    # 将 links 字段从 JSON 字符串转换为 Python 列表
    new_results = []
    for url, title, content, links in results:
        link_list = json.loads(links)
        new_results.append((url, title, content, link_list))

    return render_template('admin.html', config=config, traffic_monitor=traffic_monitor, alerts=alerts,
                           results=new_results)


@app.route('/logout', methods=['GET'])
def logout():
    session.pop('username', None)
    return redirect(url_for('login'))


if __name__ == '__main__':
    init_db()
    app.run(debug=True, threaded=True, port=5001)
