export interface LoginRequest {
  username: string;
  password: string;
}

export interface SignupRequest {
  username: string;
  password: string;
  familyName: string;
  givenName: string;
  email: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  roles: string[];
}

export interface AddressDto {
  street: string[];
  city: string;
  state: string;
  zipCode: string;
  country: string;
}

export interface ContactInfoDto {
  familyName: string;
  givenName: string;
  address: AddressDto | null;
  email: string;
  phone: string;
}

export interface CreditCardDto {
  cardNumber: string;
  cardType: string;
  expiryDate: string;
}

export interface AccountDto {
  contactInfo: ContactInfoDto | null;
  creditCard: CreditCardDto | null;
}

export interface ProfileDto {
  preferredLanguage: string;
  favoriteCategory: string;
  myListPreference: boolean;
  bannerPreference: boolean;
}

export interface CustomerResponse {
  username: string;
  roles: string[];
  account: AccountDto | null;
  profile: ProfileDto | null;
}

export interface CustomerUpdateRequest {
  account: AccountDto;
  profile: ProfileDto;
}
