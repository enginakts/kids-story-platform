# Hugging Face Space (Docker, ücretsiz CPU) veya herhangi bir sunucu için. Space 7860 portunu bekler.
FROM python:3.11-slim

RUN useradd -m -u 1000 user
USER user
ENV HOME=/home/user PATH=/home/user/.local/bin:$PATH PYTHONUNBUFFERED=1 KIKIRI_DEVICE=cpu
WORKDIR /home/user/app

COPY --chown=user requirements.txt .
RUN pip install --no-cache-dir --user torch --index-url https://download.pytorch.org/whl/cpu \
 && pip install --no-cache-dir --user -r requirements.txt
# Model imaj oluşturulurken indirilir; açılışta bekleme olmaz.
COPY --chown=user kikiri.py app.py ./
RUN python -c "from kikiri import KikiriEngine; KikiriEngine('cpu')"

EXPOSE 7860
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "7860"]
