#!/usr/bin/env python3
"""Fetch Douyin room page via Playwright browser."""
import sys, json
from playwright.sync_api import sync_playwright

def fetch(room_id):
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, args=['--no-sandbox'])
        page = browser.new_page()
        try:
            page.goto(f'https://live.douyin.com/{room_id}', wait_until='domcontentloaded', timeout=20000)
            content = page.content()
        finally:
            browser.close()
    return content

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(json.dumps({"error": "room_id required"}))
        sys.exit(1)
    result = fetch(sys.argv[1])
    print(result)
