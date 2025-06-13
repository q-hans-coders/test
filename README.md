

## “local-dream” by xororz, [GitHub](https://github.com/xororz/local-dream), Licensed under [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/deed.ko)
<br/>
<br/>

# 1. 프로젝트 개요
ZOO: Zero One Organisms ["Zero One"은 디지털 세계에서 자주 사용되는 개념으로, 
0과 1로 이루어진 디지털 동물들이 창조되는 곳이라는 의미를 담고 있습니다.]

본 작품은 유아가 직접 그린 상상의 동물을 스마트폰을 통해 촬영하고, 음성으로 프롬프트에 입력되어 AI가 디지털 캐릭터로 재창조하는 On-Device AI 기반 창작 경험 제공 어플리케이션입니다. 유아의 음성은 Whisper를 통해 텍스트로 변환되며, 그림과 함께 Stable Diffusion에 입력됩니다. Advanced Settings로 세밀하게 조정 가능하며, Inpaint 기능으로 사용자가 원하는 부분만 다시 그려주는 고급 편집 기술을 사용하여 한층 업그레이드 된 상상의 동물이 생성됩니다. 생성된 이미지는 ARCore를 통해 AR로 변환되어 유아에게 증강현실 경험을 제공합니다. 퀄컴의 온디바이스 AI 기술을 활용해 몰입도 높은 상호작용을 구현한 점이 특징입니다.


<br/>
<br/>

# 2. 팀원 및 팀 소개
| 신유빈 | 박채연 | 신현수 | 송범록 |
|:------:|:------:|:------:|:------:|
| <img src="https://github.com/user-attachments/assets/5d7564a9-d246-4e71-9f53-d6f0f711282d" alt="신유빈" width="150"> | <img src="https://github.com/user-attachments/assets/6f2cf3aa-ba91-4888-bf84-76a1bb1204a3" alt="박채" width="150"> | <img src="https://github.com/user-attachments/assets/6cf6ae49-e37d-4dcc-a2d9-8602e51c83e2" alt="신현수" width="150"> | <img src="https://github.com/user-attachments/assets/6db1bcb8-b7d1-4142-aee7-6c8b8ef7aa72" alt="송범록" width="150"> |
| TL | BE | AI | AI |
| [GitHub](https://github.com/ubin-shin) | [GitHub](https://github.com/Bigdatabomb) | [GitHub](https://github.com/shinhyun-soo) | [GitHub](https://github.com/BeomRok) |

# 3. 작품 소개

![Image](https://github.com/user-attachments/assets/561c4f79-440a-4b9b-81e7-7cc290ea70d7)


---

## 주요 기능
- **음성 프롬프트 생성**  
  Whisper 기반 음성 인식을 통해 사용자의 설명을 AI 프롬프트로 자동 변환
- **AI 이미지 생성 & Inpainting**  
  Stable Diffusion V2.1을 활용해 원본 스케치 보완 및 고해상도 디지털 아트워크 생성
- **AR 시각화**  
  ARCore를 통해 생성된 동물을 실제 공간에 배치하여 증강현실 경험 제공
- **3D 프린터 출력 지원**  
  AR 모델을 3D 프린터용 파일(STL)로 내보내어 실물 인형 제작 가능
- **온디바이스 처리**  
  Qualcomm QNN SDK 및 Snapdragon 8 Elite 칩셋 기반으로 모든 AI 연산을 디바이스 내에서 수행하여 데이터 보안성과 오프라인 접근성 확보

---

## 기술 스택

| 구분       | 세부 항목                                                                                                    |
|------------|--------------------------------------------------------------------------------------------------------------|
| **플랫폼** | Android                                                                                                      |
| **칩셋**   | Qualcomm Snapdragon 8 Elite                                                                                  |
| **개발 언어** | Kotlin, C++, Rust, Python                                                                                  |
| **개발 도구** | Android Studio, CMake, Git                                                                                 |
| **프레임워크/SDK** | ARCore, QNN SDK, local-dream (by xororz, CC BY-NC 4.0)                                                   |
| **라이브러리** | Stable Diffusion V2.1, Whisper, tokenizer-cpp                                                             |

---
## 시현 영상
https://youtube.com/shorts/puMkhkcpDlE?si=jdNh0auW4IgcdVSA
---
## 설치 및 실행

1. 레포지토리 클론  
   ```bash
   git clone https://github.com/q-hans-coders/test.git
   cd test


<!--

**Here are some ideas to get you started:**

🙋‍♀️ A short introduction - what is your organization all about?
🌈 Contribution guidelines - how can the community get involved?
👩‍💻 Useful resources - where can the community find your docs? Is there anything else the community should know?
🍿 Fun facts - what does your team eat for breakfast?
🧙 Remember, you can do mighty things with the power of [Markdown](https://docs.github.com/github/writing-on-github/getting-started-with-writing-and-formatting-on-github/basic-writing-and-formatting-syntax)
-->
