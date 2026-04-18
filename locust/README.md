## Setup virtual environment for python, activate, install locust!
```bash
# anseongjin @ anseongjin-ui-MacBookPro in ~/workspace/shop/locust on git:main x [15:21:02]
$ python3 -m venv .venv

# anseongjin @ anseongjin-ui-MacBookPro in ~/workspace/shop/locust on git:main x [15:21:45]
$ source .venv/bin/activate
(.venv)
# anseongjin @ anseongjin-ui-MacBookPro in ~/workspace/shop/locust on git:main x .venv [15:21:55]
$ pip install locust

$ locust -f stress.py
```