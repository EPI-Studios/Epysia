use proc_macro::TokenStream;
use proc_macro2::{Ident, TokenStream as TokenStream2};
use quote::quote;
use std::collections::HashMap;
use syn::punctuated::Punctuated;
use syn::{parse_macro_input, Expr, ExprLit, Field, Fields, ItemStruct, Lit, Meta, Token};

#[proc_macro_attribute]
pub fn epysia_component(attr: TokenStream, item: TokenStream) -> TokenStream {
    let mut item_struct = parse_macro_input!(item as ItemStruct);
    let component_args =
        parse_macro_input!(attr with Punctuated::<Meta, Token![,]>::parse_terminated);
    let component_args = named_exprs(&component_args);

    let struct_name = item_struct.ident.clone();
    let name_literal = string_of(&component_args, "name", struct_name.to_string());
    let category_literal = string_of(&component_args, "category", "Scripts".to_string());
    let description_literal = string_of(&component_args, "description", String::new());

    let Fields::Named(fields) = &mut item_struct.fields else {
        return syn::Error::new_spanned(
            &item_struct,
            "epysia_component requires a struct with named fields",
        )
        .into_compile_error()
        .into();
    };

    let mut property_entries = Vec::new();
    let mut getters = Vec::new();
    let mut setters = Vec::new();
    let mut default_fields = Vec::new();

    for field in fields.named.iter_mut() {
        let field_ident = field.ident.clone().expect("named field");
        let export_index = field
            .attrs
            .iter()
            .position(|attribute| attribute.path().is_ident("export"));

        let Some(export_index) = export_index else {
            default_fields.push(quote! { #field_ident: ::std::default::Default::default() });
            continue;
        };

        let export_attribute = field.attrs.remove(export_index);
        let export_args = match parse_export_args(&export_attribute) {
            Ok(args) => named_exprs(&args),
            Err(error) => return error.into_compile_error().into(),
        };

        let kind = match kind_of(field) {
            Ok(kind) => kind,
            Err(error) => return error.into_compile_error().into(),
        };

        let field_name = field_ident.to_string();
        let label = string_of(&export_args, "label", field_name.clone());
        let minimum = f32_expr_of(&export_args, "min");
        let maximum = f32_expr_of(&export_args, "max");
        let step = f32_expr_of(&export_args, "step");
        let color = bool_expr_of(&export_args, "color");
        let default_expr = export_args
            .get("default")
            .cloned()
            .unwrap_or_else(|| default_literal_for(kind));

        default_fields.push(default_field(&field_ident, kind, &default_expr));

        property_entries.push(quote! {
            epysia_sdk::PropertyDescriptor {
                name: #field_name,
                label: #label,
                kind: #kind,
                default: epysia_sdk::JsonValue::from(#default_expr),
                min: #minimum,
                max: #maximum,
                step: #step,
                color: #color,
            }
        });

        getters.push(quote! {
            #field_name => Some(epysia_sdk::JsonValue::from(self.#field_ident.clone())),
        });

        setters.push(setter_arm(&field_ident, kind, &field_name));
    }

    let expanded = quote! {
        #item_struct

        impl ::std::default::Default for #struct_name {
            fn default() -> Self {
                #struct_name {
                    #( #default_fields ),*
                }
            }
        }

        impl epysia_sdk::Component for #struct_name {
            fn descriptor() -> epysia_sdk::ComponentDescriptor {
                epysia_sdk::ComponentDescriptor {
                    name: #name_literal,
                    category: #category_literal,
                    description: #description_literal,
                    properties: ::std::vec![ #( #property_entries ),* ],
                }
            }

            fn get_json(&self, name: &str) -> Option<epysia_sdk::JsonValue> {
                match name {
                    #( #getters )*
                    _ => None,
                }
            }

            fn set_json(&mut self, name: &str, value: &epysia_sdk::JsonValue) {
                match name {
                    #( #setters )*
                    _ => {}
                }
            }
        }
    };
    expanded.into()
}

fn named_exprs(meta_list: &Punctuated<Meta, Token![,]>) -> HashMap<String, Expr> {
    let mut named = HashMap::new();
    for meta in meta_list {
        if let Meta::NameValue(name_value) = meta {
            if let Some(ident) = name_value.path.get_ident() {
                named.insert(ident.to_string(), name_value.value.clone());
            }
        }
    }
    named
}

fn parse_export_args(attribute: &syn::Attribute) -> syn::Result<Punctuated<Meta, Token![,]>> {
    if matches!(attribute.meta, Meta::Path(_)) {
        return Ok(Punctuated::new());
    }
    attribute.parse_args_with(Punctuated::<Meta, Token![,]>::parse_terminated)
}

fn string_of(named: &HashMap<String, Expr>, key: &str, fallback: String) -> String {
    match named.get(key) {
        Some(Expr::Lit(ExprLit {
            lit: Lit::Str(literal),
            ..
        })) => literal.value(),
        _ => fallback,
    }
}

fn f32_expr_of(named: &HashMap<String, Expr>, key: &str) -> TokenStream2 {
    match named.get(key) {
        Some(expr) => quote! { (#expr) as f32 },
        None => quote! { 0.0f32 },
    }
}

fn bool_expr_of(named: &HashMap<String, Expr>, key: &str) -> TokenStream2 {
    match named.get(key) {
        Some(Expr::Lit(ExprLit {
            lit: Lit::Bool(literal),
            ..
        })) => {
            let value = literal.value;
            quote! { #value }
        }
        _ => quote! { false },
    }
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Kind {
    Bool,
    I32,
    F32,
    Str,
}

impl quote::ToTokens for Kind {
    fn to_tokens(&self, tokens: &mut TokenStream2) {
        let literal = match self {
            Kind::Bool => "bool",
            Kind::I32 => "i32",
            Kind::F32 => "f32",
            Kind::Str => "string",
        };
        literal.to_tokens(tokens);
    }
}

fn kind_of(field: &Field) -> syn::Result<Kind> {
    let ty = &field.ty;
    let rendered = quote! { #ty }.to_string();
    match rendered.as_str() {
        "bool" => Ok(Kind::Bool),
        "i32" => Ok(Kind::I32),
        "f32" => Ok(Kind::F32),
        "String" => Ok(Kind::Str),
        other => Err(syn::Error::new_spanned(
            &field.ty,
            format!(
                "#[export] only supports bool, i32, f32 or String fields (found `{other}`); \
                 unsupported field types are a documented v1 limitation of the Rust language pack"
            ),
        )),
    }
}

fn default_literal_for(kind: Kind) -> Expr {
    let tokens = match kind {
        Kind::Bool => quote! { false },
        Kind::I32 => quote! { 0i32 },
        Kind::F32 => quote! { 0.0f32 },
        Kind::Str => quote! { "" },
    };
    syn::parse2(tokens).expect("literal default parses as an expression")
}

fn default_field(field_ident: &Ident, kind: Kind, default_expr: &Expr) -> TokenStream2 {
    match kind {
        Kind::Str => quote! { #field_ident: (#default_expr).to_string() },
        _ => quote! { #field_ident: #default_expr },
    }
}

fn setter_arm(field_ident: &Ident, kind: Kind, field_name: &str) -> TokenStream2 {
    match kind {
        Kind::Bool => quote! {
            #field_name => { if let Some(value) = value.as_bool() { self.#field_ident = value; } },
        },
        Kind::I32 => quote! {
            #field_name => { if let Some(value) = value.as_i64() { self.#field_ident = value as i32; } },
        },
        Kind::F32 => quote! {
            #field_name => { if let Some(value) = value.as_f64() { self.#field_ident = value as f32; } },
        },
        Kind::Str => quote! {
            #field_name => { if let Some(value) = value.as_str() { self.#field_ident = value.to_string(); } },
        },
    }
}
