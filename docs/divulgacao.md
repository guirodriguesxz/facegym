# Divulgação do FaceGym

## Legenda (post ou LinkedIn)

Projeto novo no portfólio: **FaceGym**, check-in de academia por reconhecimento facial. 🏋️‍♂️📸

O aluno olha pra câmera do totem e a API decide se libera a entrada: plano ativo, horário do plano,
limite de acessos na semana e bloqueio manual. Se o rosto não bater com certeza, o totem pede o CPF.

O que eu quis treinar com ele:
🔹 **Java 21 + Spring Boot com arquitetura hexagonal**: as regras de acesso ficam em Java puro, e um
teste (ArchUnit) quebra o build se o domínio depender de framework
🔹 **Integração resiliente**: timeout, retry e circuit breaker (Resilience4j). Se a biometria cair,
o check-in continua por CPF
🔹 **Serviço de biometria em Python** (FastAPI + InsightFace), com os limiares calibrados num dataset público
🔹 **LGPD**: a foto nunca é guardada, só um vetor numérico do rosto, com consentimento
🔹 **72 testes na API**, com Testcontainers e WireMock
🔹 Deploy no Render, Neon e Vercel

Dá pra testar com o seu rosto: em "Teste com você" você fica cadastrado por 10 minutos e depois é
tudo apagado sozinho.

🔗 Demo: facegym-web.vercel.app
💻 Código: github.com/guirodriguesxz/facegym

(Os servidores são do plano gratuito, então o primeiro acesso pode levar até 1 minuto pra acordar.)

#java #springboot #backend #python #react #desenvolvedor #programacao #portfolio

## Textos curtos para os stories

1. Projeto novo no portfólio 🚀 Check-in de academia por reconhecimento facial.
2. Seu rosto vira um vetor de números. A foto nunca é guardada.
3. Java + Spring com arquitetura hexagonal, biometria em Python e circuit breaker.
4. Testa com o seu rosto! Link aqui 👇 (usar o adesivo de link com facegym-web.vercel.app)

## Dicas para os stories

- Grave a tela do celular fazendo o "Teste com você" (cadastrar → check-in → "Bem-vindo") e poste
  entre o story 2 e o 3: mostrar funcionando é o que mais chama atenção.
- No último story, use o **adesivo de link** do Instagram com `https://facegym-web.vercel.app`.
- Abra o site uns 2 minutos antes de postar, para os servidores já estarem acordados.
