export type Role = "CUSTOMER" | "ADMIN";

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  createdAt: string | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserResponse;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface ErrorResponse {
  code: number;
  message: string;
  timestamp: string;
}
