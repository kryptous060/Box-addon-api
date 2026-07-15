# Box API Documentation

This API allows external control over the Box Android app.

## Endpoints

### 1. Chat
`POST /chat`
- **Request Body**: `{"message": "string"}`
- **Description**: Triggers a response from the currently loaded LLM.

### 2. Generate Image
`POST /generate-image`
- **Request Body**: `{"prompt": "string"}`
- **Description**: Triggers image generation using the currently loaded image generation model.

### 3. Load LLM
`POST /load-llm`
- **Request Body**: `{"modelName": "string"}`
- **Description**: Loads a specified LLM model.

### 4. Load Image Model
`POST /load-image-model`
- **Request Body**: `{"modelName": "string"}`
- **Description**: Loads a specified image generation model.

---
## LLM Tool Use Instructions
When acting as an agent, you can utilize the `Box` API to perform tasks.

To generate an image:
1. Ensure an image generation model is loaded (use `/load-image-model` if necessary).
2. Call `POST /generate-image` with the desired prompt.

To chat:
1. Ensure an LLM is loaded (use `/load-llm` if necessary).
2. Call `POST /chat` with your query.
